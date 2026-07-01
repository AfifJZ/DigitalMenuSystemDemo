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

    @Value("${sendgrid.api-key:}")
    private String sendgridApiKey;

    @Value("${sendgrid.api-base-url:https://api.sendgrid.com}")
    private String sendgridApiBaseUrl;

    @Value("${app.mail.from:}")
    private String fromAddress;

    /** When true, logs OTP to the application log if email sending is unavailable or fails. */
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
        log.info("Email providers configured: Mailgun={}, SendGrid={}, ConsoleFallback={}",
                isMailgunConfigured(), isSendGridConfigured(), consoleFallback);

        try {
            String from = fromAddress.isBlank() ? "postmaster@" + mailgunDomain : fromAddress;
            if (isSendGridConfigured()) {
                log.info("Sending email via SendGrid API - From: {}", from);
                String body = introLine + "\n\n"
                        + "Your verification code is:\n\n"
                        + "    " + code + "\n\n"
                        + "This code expires in 15 minutes.\n\n"
                        + "If you did not request this, you can ignore this email.";
                sendViaSendGrid(from, toEmail.trim(), subject, body);
                log.info("✅ OTP email successfully sent to {} via SendGrid", toEmail);
                return true;
            }
            if (isMailgunConfigured()) {
                log.info("Sending email via Mailgun API - Domain: {}, From: {}", mailgunDomain, from);
                String body = introLine + "\n\n"
                        + "Your verification code is:\n\n"
                        + "    " + code + "\n\n"
                        + "This code expires in 15 minutes.\n\n"
                        + "If you did not request this, you can ignore this email.";
                sendViaMailgun(from, toEmail.trim(), subject, body);
                log.info("✅ OTP email successfully sent to {} via Mailgun", toEmail);
                return true;
            }
        } catch (Exception e) {
            log.error("❌ Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            log.error("Email send error details:", e);
            if (consoleFallback) {
                log.warn("Email send failed, falling back to OTP log output because app.mail.console-fallback is enabled.");
                logOtpFallback(toEmail, code, label);
                return true;
            }
        }

        if (consoleFallback) {
            logOtpFallback(toEmail, code, label);
            return true;
        }

        return false;
    }

    public boolean isMailgunConfigured() {
        return mailgunApiKey != null
                && !mailgunApiKey.isBlank()
                && mailgunDomain != null
                && !mailgunDomain.isBlank();
    }

    public boolean isSendGridConfigured() {
        return sendgridApiKey != null && !sendgridApiKey.isBlank();
    }

    public boolean isUsingConsoleFallback() {
        return !isMailgunConfigured() && !isSendGridConfigured() && consoleFallback;
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

    private void sendViaSendGrid(String from, String to, String subject, String text) throws Exception {
        String requestBody = "{"
                + "\"personalizations\": [{\"to\": [{\"email\": \"" + escapeJson(to) + "\"}]}],"
                + "\"from\": {\"email\": \"" + escapeJson(from) + "\"},"
                + "\"subject\": \"" + escapeJson(subject) + "\","
                + "\"content\": [{\"type\": \"text/plain\", \"value\": \"" + escapeJson(text) + "\"}]"
                + "}";

        String url = sendgridApiBaseUrl + "/v3/mail/send";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + sendgridApiKey)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("SendGrid send failed: " + response.statusCode() + " " + response.body());
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void logOtpFallback(String toEmail, String code, String label) {
        log.info("OTP CODE ({}) dev fallback — Email: {}, Code: {}", label, toEmail, code);
    }
}
