package com.example.menumanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmtpConnectivityChecker {

    private static final Logger log = LoggerFactory.getLogger(SmtpConnectivityChecker.class);

    @Value("${mailgun.api-base-url:https://api.mailgun.net}")
    private String mailgunApiBaseUrl;

    @Bean
    public ApplicationRunner smtpChecker() {
        return args -> log.info("Mailgun API base URL configured as {}", mailgunApiBaseUrl);
    }
}
