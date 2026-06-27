package com.example.menumanager.service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Generates 6-digit OTP codes and stores them temporarily in memory.
 * Codes expire after 15 minutes.
 */
@Service
public class OtpService {

    public static final long OTP_TTL_MS = 15 * 60 * 1000;

    public enum Purpose {
        REGISTRATION,
        PASSWORD_RESET
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    public String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    /** Saves OTP keyed by purpose + email (lowercase). */
    public String createOtp(Purpose purpose, String email, String payload) {
        String code = generateCode();
        String key = key(purpose, email);
        store.put(key, new OtpEntry(code, payload, System.currentTimeMillis() + OTP_TTL_MS));
        return code;
    }

    public Optional<OtpEntry> verify(Purpose purpose, String email, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String key = key(purpose, email);
        OtpEntry entry = store.get(key);
        if (entry == null || entry.isExpired() || !entry.code().equals(code.trim())) {
            return Optional.empty();
        }
        store.remove(key);
        return Optional.of(entry);
    }

    public void clear(Purpose purpose, String email) {
        store.remove(key(purpose, email));
    }

    private String key(Purpose purpose, String email) {
        return purpose.name() + ":" + email.trim().toLowerCase();
    }

    public record OtpEntry(String code, String payload, long expiresAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }
}
