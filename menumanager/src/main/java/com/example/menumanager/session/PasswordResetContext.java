package com.example.menumanager.session;

import java.io.Serializable;

public class PasswordResetContext implements Serializable {

    public static final String SESSION_KEY = "passwordResetContext";

    public enum TargetType { ORGANIZATION, BRANCH }

    private TargetType targetType;
    private Long targetId;
    private String displayName;
    private long expiresAtMillis;

    public TargetType getTargetType() { return targetType; }
    public void setTargetType(TargetType targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public long getExpiresAtMillis() { return expiresAtMillis; }
    public void setExpiresAtMillis(long expiresAtMillis) { this.expiresAtMillis = expiresAtMillis; }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAtMillis;
    }
}
