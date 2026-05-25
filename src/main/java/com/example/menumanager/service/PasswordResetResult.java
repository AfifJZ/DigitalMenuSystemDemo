package com.example.menumanager.service;

import java.util.Optional;

import com.example.menumanager.session.PasswordResetContext;

public record PasswordResetResult(Optional<String> error, Optional<PasswordResetContext> context) {

    public static PasswordResetResult ok(PasswordResetContext context) {
        return new PasswordResetResult(Optional.empty(), Optional.of(context));
    }

    public static PasswordResetResult fail(String message) {
        return new PasswordResetResult(Optional.of(message), Optional.empty());
    }
}
