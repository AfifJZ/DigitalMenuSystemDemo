package com.example.menumanager.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${mailgun.api-key:${MAIL_PASSWORD:}}")
    private String mailgunApiKey;

    @Value("${mailgun.domain:sandbox269e1414295f4ff6aaf977fb5417036e.mailgun.org}")
    private String mailgunDomain;

    @Value("${mailgun.api-base-url:https://api.mailgun.net}")
    private String mailgunApiBaseUrl;

    @Value("${app.mail.from:}")
    private String fromAddress;

    /** When true, prints OTP to the terminal if Mailgun is not configured or send fails. */
    @Value("${app.mail.console-fallback:true}")
    private boolean consoleFallback;

    public boolean sendRegistrationOtp(String toEmail, String code) {
        return deliverOtp(
                toEmail,
                code,
                "REGISTRATION",
                "Verify your Menu Manager account",
                "Thanks for registering! Enter this code to verify your email address."
        );
    }

    public boolean sendPasswordResetOtp(String toEmail, String code) {
        return deliverOtp(
                toEmail,
                code,
                "PASSWORD RESET",
                "Password reset verification code",
                "You requested to reset your password. Enter this code to continue."
        );
    }

    /**
     * Generic verification-code sender used by the legacy controller flow
     * (ManageAuthController.forgot-password). It just delegates to the
     * password-reset OTP sender since the caller is generating a
     * PASSWORD_RESET purpose OTP via OtpService.
     */
    public boolean sendVerificationCode(String toEmail, String code) {
        return sendPasswordResetOtp(toEmail, code);
    }

    private boolean deliverOtp(String toEmail, String code, String label, String subject, String introLine) {
        log.info("Attempting to send OTP email to: {}", toEmail);
        log.info("Mail configured: {}, Using console fallback: {}", isMailConfigured(), isUsingConsoleFallback());
        
        if (isMailConfigured()) {
            try {
                String from = fromAddress.isBlank() ? "postmaster@" + mailgunDomain : fromAddress;
                log.info("Sending email via Mailgun API - Domain: {}, From: {}", mailgunDomain, from);
                String body = introLine + "\n\n"
                        + "Your verification code is:\n\n"
                        + "    " + code + "\n\n"
                        + "This code expires in 15 minutes.\n\n"
                        + "If you did not request this, you can ignore this email.";
                sendViaMailgun(from, toEmail.trim(), subject, body);
                log.info("✅ OTP email successfully sent to {}", toEmail);
                return true;
            } catch (Exception e) {
                log.error("❌ Failed to send OTP email to {}: {}", toEmail, e.getMessage());
                log.error("Error details:", e);
            }
        } else {
            log.warn("Mail not configured. Mailgun domain: '{}', Fallback enabled: {}", 
                mailgunDomain, consoleFallback);
        }

        if (consoleFallback) {
            printOtpToConsole(toEmail, code, label);
            return true;
        }

        return false;
    }

    public boolean isMailConfigured() {
        return mailgunApiKey != null
                && !mailgunApiKey.isBlank()
                && mailgunDomain != null
                && !mailgunDomain.isBlank();
    }

    public boolean isUsingConsoleFallback() {
        return !isMailConfigured() && consoleFallback;
    }

    private void sendViaMailgun(String from, String to, String subject, String text) throws Exception {
        String requestBody = "from=" + encode(from)
                + "&to=" + encode(to)
                + "&subject=" + encode(subject)
                + "&text=" + encode(text);

        String url = mailgunApiBaseUrl + "/v3/" + mailgunDomain + "/messages";
        String auth = Base64.getEncoder().encodeToString(("api:" + mailgunApiKey).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Mailgun send failed: " + response.statusCode() + " " + response.body());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void printOtpToConsole(String toEmail, String code, String label) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("  OTP CODE (" + label + ") — dev mode (SMTP not configured or send failed)");
        System.out.println("  Email : " + toEmail);
        System.out.println("  Code  : " + code);
        System.out.println("  Copy the code above into the website form.");
        System.out.println("============================================================");
        System.out.println();
    }
}
