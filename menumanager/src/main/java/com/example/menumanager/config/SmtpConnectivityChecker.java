package com.example.menumanager.config;

import java.net.InetSocketAddress;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmtpConnectivityChecker {

    private static final Logger log = LoggerFactory.getLogger(SmtpConnectivityChecker.class);

    @Value("${spring.mail.host:smtp.mailgun.org}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Value("${spring.mail.properties.mail.smtp.connectiontimeout:5000}")
    private int timeout;

    @Bean
    public ApplicationRunner smtpChecker() {
        return args -> {
            try (Socket socket = new Socket()) {
                InetSocketAddress addr = new InetSocketAddress(mailHost, mailPort);
                socket.connect(addr, timeout);
                log.info("SMTP connectivity check: success to {}:{}", mailHost, mailPort);
            } catch (Exception e) {
                log.error("SMTP connectivity check: failed to connect to {}:{} — {}", mailHost, mailPort, e.getMessage());
            }
        };
    }
}
