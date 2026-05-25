package com.example.menumanager.service;

import java.util.Optional;

public record BranchSetupResult(Optional<String> error, Optional<Long> branchId) {

    public static BranchSetupResult success(long branchId) {
        return new BranchSetupResult(Optional.empty(), Optional.of(branchId));
    }

    public static BranchSetupResult failure(String message) {
        return new BranchSetupResult(Optional.of(message), Optional.empty());
    }
}
