package com.example.menumanager.util;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class PasswordUtil {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private static final String AES_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_IV_LEN = 12;   // 96 bits
    private static final int GCM_TAG_LEN = 128; // bits
    private static final int AES_KEY_LEN = 16;  // 128-bit key

    private PasswordUtil() {}

    public static String hash(String raw) {
        return ENCODER.encode(raw);
    }

    public static boolean matches(String raw, String hash) {
        return ENCODER.matches(raw, hash);
    }

    // ─── Reversible encryption (for branch password display) ───────────────────────

    /**
     * Encrypts a plaintext password using AES-128-GCM with the given secret key.
     * The returned string is Base64( IV ‖ ciphertext ) and is safe to store in a column.
     */
    public static String encrypt(String plaintext, String secret) {
        try {
            byte[] keyBytes = deriveKey(secret);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = new byte[GCM_IV_LEN];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LEN, iv);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            // Prepend IV
            byte[] combined = new byte[GCM_IV_LEN + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LEN);
            System.arraycopy(ciphertext, 0, combined, GCM_IV_LEN, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt password", e);
        }
    }

    /**
     * Decrypts a Base64( IV ‖ ciphertext ) string back to the original plaintext.
     */
    public static String decrypt(String encrypted, String secret) {
        try {
            byte[] keyBytes = deriveKey(secret);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");

            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length < GCM_IV_LEN) {
                throw new IllegalArgumentException("Invalid encrypted payload");
            }

            byte[] iv = new byte[GCM_IV_LEN];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LEN);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LEN, iv);

            byte[] ciphertext = new byte[combined.length - GCM_IV_LEN];
            System.arraycopy(combined, GCM_IV_LEN, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            return new String(cipher.doFinal(ciphertext), "UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt password", e);
        }
    }

    /**
     * Derives a 128-bit AES key from an arbitrary-length secret using a simple
     * SHA-256 hash-then-truncate approach.
     */
    private static byte[] deriveKey(String secret) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(secret.getBytes("UTF-8"));
            byte[] key = new byte[AES_KEY_LEN];
            System.arraycopy(hash, 0, key, 0, AES_KEY_LEN);
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive key", e);
        }
    }
}
