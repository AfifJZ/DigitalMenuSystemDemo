package com.example.menumanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${MAIL_USERNAME:}")
    private String mailUsername;

    @Value("${MAIL_FROM:}")
    private String fromAddress;

    /** When true, prints OTP to the terminal if SMTP is not configured (great for local testing). */
    @Value("${MAIL_CONSOLE_FALLBACK:true}")
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
                log.info("Sending email via SMTP - Host: {}, Username: {}", 
                    System.getProperty("spring.mail.host", "not set"), 
                    System.getProperty("spring.mail.username", "not set"));
                
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress.isBlank() ? mailUsername : fromAddress);
                message.setTo(toEmail.trim());
                message.setSubject(subject);
                message.setText(
                        introLine + "\n\n"
                        + "Your verification code is:\n\n"
                        + "    " + code + "\n\n"
                        + "This code expires in 15 minutes.\n\n"
                        + "If you did not request this, you can ignore this email."
                );
                mailSender.send(message);
                log.info("✅ OTP email successfully sent to {}", toEmail);
                return true;
            } catch (Exception e) {
                log.error("❌ Failed to send OTP email to {}: {}", toEmail, e.getMessage());
                log.error("Error details:", e);
            }
        } else {
            log.warn("Mail not configured. Mail username: '{}', Fallback enabled: {}", 
                mailUsername, consoleFallback);
        }

        if (consoleFallback) {
            printOtpToConsole(toEmail, code, label);
            return true;
        }

        return false;
    }

    public boolean isMailConfigured() {
        return mailUsername != null
                && !mailUsername.isBlank()
                && !mailUsername.startsWith("REPLACE_")
                && !mailUsername.startsWith("your-");
    }

    public boolean isUsingConsoleFallback() {
        return !isMailConfigured() && consoleFallback;
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
