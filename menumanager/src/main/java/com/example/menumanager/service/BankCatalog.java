package com.example.menumanager.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Static catalogue of Malaysian banks supported for Stripe Connect payouts,
 * together with the typical account-number length range we expect for each.
 *
 * <p>Length ranges are based on the standard Malaysian bank account number
 * formats. They are used for client-side validation hints and server-side
 * sanity checks. The service is intentionally simple: a JSON-shaped map
 * (no database, no external tool) so the list is easy to read / edit in
 * one place and tests don't need a fixture.
 */
public final class BankCatalog {

    /** Immutable record describing one supported bank. */
    public record BankSpec(
            String code,        // canonical display name (also used as form value)
            int minLength,      // minimum allowed digit count
            int maxLength,      // maximum allowed digit count
            String hint        // short text shown under the account-number input
    ) {
        public String regex() {
            return "\\d{" + minLength + "," + maxLength + "}";
        }
    }

    /** Linked map = stable dropdown order. */
    private static final Map<String, BankSpec> CATALOG = new LinkedHashMap<>();

    static {
        register("Maybank",        10, 12, "Typically 10–12 digits (no dashes).");
        register("CIMB Bank",      10, 14, "Typically 10–14 digits.");
        register("Public Bank",    10, 12, "Typically 10–12 digits.");
        register("RHB Bank",       10, 16, "Typically 10–16 digits.");
        register("Hong Leong Bank",10, 14, "Typically 10–14 digits.");
        register("AmBank",         10, 14, "Typically 10–14 digits.");
        register("Bank Islam",     14, 14, "Exactly 14 digits.");
        register("BSN",            10, 10, "Exactly 10 digits.");
        register("Bank Rakyat",    14, 14, "Exactly 14 digits.");
        register("Bank Muamalat",  14, 14, "Exactly 14 digits.");
        register("Affin Bank",     10, 14, "Typically 10–14 digits.");
        register("Alliance Bank",  12, 16, "Typically 12–16 digits.");
        register("HSBC Bank",      10, 12, "Typically 10–12 digits.");
        register("OCBC Bank",      10, 14, "Typically 10–14 digits.");
        register("Standard Chartered", 10, 12, "Typically 10–12 digits.");
        register("UOB",            10, 12, "Typically 10–12 digits.");
    }

    private static void register(String code, int min, int max, String hint) {
        CATALOG.put(code, new BankSpec(code, min, max, hint));
    }

    private BankCatalog() {}

    /** All banks in display order, suitable for a <code><select></code>. */
    public static List<BankSpec> all() {
        return List.copyOf(CATALOG.values());
    }

    /**
     * Look up a bank by its canonical name. Tries an exact (case-insensitive)
     * match first, then falls back to matching by simple equality.
     */
    public static BankSpec find(String name) {
        if (name == null) return null;
        BankSpec exact = CATALOG.get(name);
        if (exact != null) return exact;
        for (BankSpec b : CATALOG.values()) {
            if (b.code().equalsIgnoreCase(name.trim())) return b;
        }
        return null;
    }

    /**
     * Default length range (used when the selected bank is unknown or hasn't
     * been picked yet) so the form still submits with a sensible validation
     * window.
     */
    public static BankSpec fallback() {
        return new BankSpec("", 8, 20, "Digits only, 8–20 characters.");
    }

    /**
     * Validate a (bank, accountNumber) pair. Returns an empty Optional if
     * the number is acceptable for the bank, or a user-friendly error message
     * otherwise.
     */
    public static java.util.Optional<String> validate(String bankName, String accountNumber) {
        BankSpec bank = Objects.requireNonNullElse(find(bankName), fallback());
        if (accountNumber == null || accountNumber.isBlank()) {
            return java.util.Optional.of("Account number is required.");
        }
        String trimmed = accountNumber.replaceAll("\\s+", "");
        if (!trimmed.matches("\\d+")) {
            return java.util.Optional.of("Account number must contain digits only.");
        }
        if (trimmed.length() < bank.minLength() || trimmed.length() > bank.maxLength()) {
            return java.util.Optional.of(
                    bank.code().isBlank()
                            ? "Account number must be " + bank.minLength() + "–" + bank.maxLength() + " digits."
                            : bank.code() + " account numbers are " + bank.minLength() + "–" + bank.maxLength() + " digits.");
        }
        return java.util.Optional.empty();
    }
}
