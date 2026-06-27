package com.example.menumanager.session;

import java.io.Serializable;

/** Holds pending registration data while the user verifies their email. */
public class RegistrationContext implements Serializable {

    public static final String SESSION_KEY = "registrationContext";

    private String organizationName;
    private String email;
    private String passwordHash;
    private long expiresAtMillis;

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public long getExpiresAtMillis() { return expiresAtMillis; }
    public void setExpiresAtMillis(long expiresAtMillis) { this.expiresAtMillis = expiresAtMillis; }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAtMillis;
    }
}
