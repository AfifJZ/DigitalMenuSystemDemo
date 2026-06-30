package com.example.menumanager.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.menumanager.model.Branch;
import com.example.menumanager.model.Organization;
import com.example.menumanager.repository.BranchRepository;
import com.example.menumanager.repository.OrganizationRepository;
import com.example.menumanager.service.OtpService.Purpose;
import com.example.menumanager.session.ManagerSession;
import com.example.menumanager.session.PasswordResetContext;
import com.example.menumanager.session.PasswordResetContext.TargetType;
import com.example.menumanager.session.RegistrationContext;
import com.example.menumanager.util.PasswordUtil;

import jakarta.servlet.http.HttpSession;

@Service
public class ManagerAuthService {

    @Autowired private OrganizationRepository organizationRepo;
    @Autowired private BranchRepository branchRepo;
    @Autowired private EmailService emailService;
    @Autowired private OtpService otpService;
    @Autowired private StripeConnectService stripeConnectService;

    @Value("${app.crypto.secret}")
    private String cryptoSecret;

    /** Step 1 of registration: validate details, store pending data, send OTP. */
    public Optional<String> startRegistration(HttpSession httpSession, String orgName, String email, String password) {
        if (orgName == null || orgName.isBlank()) return Optional.of("Organization name is required.");
        if (email == null || email.isBlank()) return Optional.of("Email is required.");
        if (!email.contains("@")) return Optional.of("Please enter a valid email address.");
        if (password == null || password.length() < 6) return Optional.of("Password must be at least 6 characters.");

        String trimmedName = orgName.trim();
        String trimmedEmail = email.trim();

        if (organizationRepo.existsByNameIgnoreCase(trimmedName)) {
            return Optional.of("An organization with this name already exists.");
        }
        if (organizationRepo.existsByEmailIgnoreCase(trimmedEmail)) {
            return Optional.of("This email is already registered.");
        }

        RegistrationContext pending = new RegistrationContext();
        pending.setOrganizationName(trimmedName);
        pending.setEmail(trimmedEmail);
        pending.setPasswordHash(PasswordUtil.hash(password));
        pending.setExpiresAtMillis(System.currentTimeMillis() + OtpService.OTP_TTL_MS);
        saveRegistrationContext(httpSession, pending);

        String code = otpService.createOtp(Purpose.REGISTRATION, trimmedEmail, "pending");
        if (!emailService.sendRegistrationOtp(trimmedEmail, code)) {
            clearRegistrationContext(httpSession);
            otpService.clear(Purpose.REGISTRATION, trimmedEmail);
            return Optional.of("Could not send verification email. Check email settings in application.properties.");
        }
        return Optional.empty();
    }

    /** Step 2 of registration: verify OTP and create the organization account. */
    @Transactional
    public Optional<String> completeRegistration(HttpSession httpSession, String code) {
        RegistrationContext pending = getRegistrationContext(httpSession);
        if (pending == null || pending.isExpired()) {
            return Optional.of("Verification session expired. Please register again.");
        }

        var otpEntry = otpService.verify(Purpose.REGISTRATION, pending.getEmail(), code);
        if (otpEntry.isEmpty()) {
            return Optional.of("Invalid or expired verification code.");
        }

        if (organizationRepo.existsByNameIgnoreCase(pending.getOrganizationName())
                || organizationRepo.existsByEmailIgnoreCase(pending.getEmail())) {
            clearRegistrationContext(httpSession);
            return Optional.of("This organization or email was already registered.");
        }

        Organization org = new Organization();
        org.setName(pending.getOrganizationName());
        org.setEmail(pending.getEmail());
        org.setPasswordHash(pending.getPasswordHash());
        org.setBranchLimit(10);
        org.setEmailVerified(true);
        organizationRepo.save(org);

        clearRegistrationContext(httpSession);
        return Optional.empty();
    }

    public Optional<String> resendRegistrationOtp(HttpSession httpSession) {
        RegistrationContext pending = getRegistrationContext(httpSession);
        if (pending == null || pending.isExpired()) {
            return Optional.of("Verification session expired. Please register again.");
        }
        pending.setExpiresAtMillis(System.currentTimeMillis() + OtpService.OTP_TTL_MS);
        saveRegistrationContext(httpSession, pending);

        String newCode = otpService.createOtp(Purpose.REGISTRATION, pending.getEmail(), "pending");
        if (!emailService.sendRegistrationOtp(pending.getEmail(), newCode)) {
            return Optional.of("Could not resend email. Check email settings in application.properties.");
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<ManagerSession> loginOrganization(String orgName, String password) {
        if (orgName == null || orgName.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }
        return organizationRepo.findByNameIgnoreCase(orgName.trim())
                .filter(org -> org.isEmailVerified())
                .filter(org -> PasswordUtil.matches(password, org.getPasswordHash()))
                .map(org -> {
                    ManagerSession session = new ManagerSession();
                    session.setOrganizationId(org.getId());
                    session.setOrganizationName(org.getName());
                    session.setOrganizationLevel(true);
                    branchRepo.findByOrganizationIdOrderByNameAsc(org.getId()).stream()
                            .filter(Branch::isSetupComplete)
                            .findFirst()
                            .ifPresent(branch -> applyBranch(session, branch));
                    return session;
                });
    }

    /**
     * Tries organization login first, then branch login (same account name field).
     */
    @Transactional(readOnly = true)
    public Optional<ManagerSession> login(String accountName, String password) {
        if (accountName == null || accountName.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }
        Optional<ManagerSession> orgSession = loginOrganization(accountName.trim(), password);
        if (orgSession.isPresent()) {
            return orgSession;
        }
        return loginBranch(accountName.trim(), password);
    }

    @Transactional(readOnly = true)
    public Optional<ManagerSession> loginBranch(String branchName, String password) {
        if (branchName == null || branchName.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }
        List<Branch> matches = branchRepo.findAllByNameIgnoreCase(branchName.trim()).stream()
                .filter(Branch::isSetupComplete)
                .filter(b -> PasswordUtil.matches(password, b.getPasswordHash()))
                .toList();

        if (matches.isEmpty()) return Optional.empty();
        if (matches.size() > 1) {
            return Optional.empty();
        }

        Branch branch = matches.get(0);
        ManagerSession session = new ManagerSession();
        session.setOrganizationId(branch.getOrganization().getId());
        session.setOrganizationName(branch.getOrganization().getName());
        session.setOrganizationLevel(false);
        applyBranch(session, branch);
        return Optional.of(session);
    }

    @Transactional
    public BranchSetupResult setupBranch(Long organizationId, String name, String location,
                                         int tableCount, String password) {
        if (name == null || name.isBlank()) return BranchSetupResult.failure("Branch name is required.");
        if (location == null || location.isBlank()) return BranchSetupResult.failure("Location is required.");
        if (tableCount < 1) return BranchSetupResult.failure("Number of tables must be at least 1.");
        if (password == null || password.length() < 4) {
            return BranchSetupResult.failure("Branch password must be at least 4 characters.");
        }

        Organization org = organizationRepo.findById(organizationId).orElse(null);
        if (org == null) return BranchSetupResult.failure("Organization not found.");

        if (branchRepo.existsByOrganization_IdAndNameIgnoreCase(organizationId, name.trim())) {
            return BranchSetupResult.failure("A branch with this name already exists in your organization.");
        }

        Branch branch = new Branch();
        branch.setOrganization(org);
        branch.setName(name.trim());
        branch.setLocation(location.trim());
        branch.setTableCount(tableCount);
        branch.setPasswordHash(PasswordUtil.hash(password));
        branch.setEncryptedPassword(PasswordUtil.encrypt(password, cryptoSecret));
        branch.setSetupComplete(true);
        branchRepo.save(branch);
        return BranchSetupResult.success(branch.getId());
    }

    public boolean needsBranchSetup(ManagerSession session) {
        if (session == null || !session.isOrganizationLevel()) {
            return false;
        }
        List<Branch> completeBranches = branchRepo.findByOrganizationIdOrderByNameAsc(session.getOrganizationId()).stream()
                .filter(Branch::isSetupComplete)
                .toList();
        if (completeBranches.isEmpty()) {
            return true;
        }
        if (session.getBranchId() == null) {
            applyBranch(session, completeBranches.get(0));
        }
        return false;
    }

    public void ensureBranchSelected(ManagerSession session) {
        if (session == null || session.getBranchId() != null) {
            return;
        }
        needsBranchSetup(session);
    }

    public List<Branch> getBranchesForOrganization(Long organizationId) {
        return branchRepo.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .filter(Branch::isSetupComplete)
                .toList();
    }

    public Optional<Branch> getBranch(Long branchId) {
        return branchRepo.findById(branchId);
    }

    public void switchBranch(ManagerSession session, Long branchId) {
        if (session == null || !session.isOrganizationLevel()) return;
        branchRepo.findById(branchId)
                .filter(b -> b.getOrganization().getId().equals(session.getOrganizationId()))
                .filter(Branch::isSetupComplete)
                .ifPresent(branch -> applyBranch(session, branch));
    }

    public void saveSession(HttpSession httpSession, ManagerSession managerSession) {
        httpSession.setAttribute(ManagerSession.SESSION_KEY, managerSession);
    }

    public ManagerSession getSession(HttpSession httpSession) {
        Object value = httpSession.getAttribute(ManagerSession.SESSION_KEY);
        return value instanceof ManagerSession ms ? ms : null;
    }

    public void clearSession(HttpSession httpSession) {
        httpSession.removeAttribute(ManagerSession.SESSION_KEY);
    }

    @Transactional
    public Optional<String> updateBranchDetails(Long branchId, Long organizationId, String location, int tableCount) {
        if (tableCount < 1 || tableCount > 500) {
            return Optional.of("Number of tables must be between 1 and 500.");
        }
        Branch branch = branchRepo.findById(branchId).orElse(null);
        if (branch == null || !branch.getOrganization().getId().equals(organizationId)) {
            return Optional.of("Branch not found.");
        }
        if (location != null && !location.isBlank()) {
            branch.setLocation(location.trim());
        }
        branch.setTableCount(tableCount);
        branchRepo.save(branch);
        return Optional.empty();
    }

    @Transactional
    public Optional<String> updateBranchPassword(Long branchId, Long organizationId, String newPassword) {
        if (newPassword == null || newPassword.length() < 4) {
            return Optional.of("Password must be at least 4 characters.");
        }
        Branch branch = branchRepo.findById(branchId).orElse(null);
        if (branch == null || !branch.getOrganization().getId().equals(organizationId)) {
            return Optional.of("Branch not found.");
        }
        branch.setPasswordHash(PasswordUtil.hash(newPassword));
        branch.setEncryptedPassword(PasswordUtil.encrypt(newPassword, cryptoSecret));
        branchRepo.save(branch);
        return Optional.empty();
    }

    public PasswordResetResult startPasswordReset(String resetType, String organizationName,
                                                  String branchName, String email) {
        if (email == null || email.isBlank()) {
            return PasswordResetResult.fail("Email is required.");
        }
        String normalizedEmail = email.trim();

        if ("branch".equalsIgnoreCase(resetType)) {
            if (branchName == null || branchName.isBlank() || organizationName == null || organizationName.isBlank()) {
                return PasswordResetResult.fail("Branch name and organization name are required.");
            }
            Organization org = organizationRepo.findByNameIgnoreCase(organizationName.trim()).orElse(null);
            if (org == null || !org.getEmail().equalsIgnoreCase(normalizedEmail)) {
                return PasswordResetResult.fail("No matching branch account found for these details.");
            }
            Branch branch = branchRepo.findByOrganization_IdAndNameIgnoreCase(org.getId(), branchName.trim())
                    .filter(Branch::isSetupComplete)
                    .orElse(null);
            if (branch == null) {
                return PasswordResetResult.fail("No matching branch account found for these details.");
            }
            return PasswordResetResult.ok(buildResetContext(TargetType.BRANCH, branch.getId(), branch.getName()));
        }

        if (organizationName == null || organizationName.isBlank()) {
            return PasswordResetResult.fail("Organization name is required.");
        }
        Organization org = organizationRepo.findByNameIgnoreCase(organizationName.trim()).orElse(null);
        if (org == null || !org.getEmail().equalsIgnoreCase(normalizedEmail)) {
            return PasswordResetResult.fail("No matching organization account found for these details.");
        }
        return PasswordResetResult.ok(buildResetContext(TargetType.ORGANIZATION, org.getId(), org.getName()));
    }

    @Transactional
    public Optional<String> completePasswordReset(HttpSession httpSession, String newPassword) {
        PasswordResetContext ctx = getPasswordResetContext(httpSession);
        if (ctx == null || ctx.isExpired()) {
            return Optional.of("Reset session expired. Please start again.");
        }
        int minLen = ctx.getTargetType() == TargetType.ORGANIZATION ? 6 : 4;
        if (newPassword == null || newPassword.length() < minLen) {
            return Optional.of("Password must be at least " + minLen + " characters.");
        }
        if (ctx.getTargetType() == TargetType.ORGANIZATION) {
            Organization org = organizationRepo.findById(ctx.getTargetId()).orElse(null);
            if (org == null) {
                return Optional.of("Account not found.");
            }
            org.setPasswordHash(PasswordUtil.hash(newPassword));
            organizationRepo.save(org);
        } else {
            Branch branch = branchRepo.findById(ctx.getTargetId()).orElse(null);
            if (branch == null) {
                return Optional.of("Account not found.");
            }
            branch.setPasswordHash(PasswordUtil.hash(newPassword));
            branchRepo.save(branch);
        }
        clearPasswordResetContext(httpSession);
        return Optional.empty();
    }

    public void savePasswordResetContext(HttpSession httpSession, PasswordResetContext context) {
        httpSession.setAttribute(PasswordResetContext.SESSION_KEY, context);
    }

    public PasswordResetContext getPasswordResetContext(HttpSession httpSession) {
        Object value = httpSession.getAttribute(PasswordResetContext.SESSION_KEY);
        return value instanceof PasswordResetContext ctx ? ctx : null;
    }

    public void clearPasswordResetContext(HttpSession httpSession) {
        httpSession.removeAttribute(PasswordResetContext.SESSION_KEY);
    }

    private PasswordResetContext buildResetContext(TargetType type, Long targetId, String displayName) {
        PasswordResetContext ctx = new PasswordResetContext();
        ctx.setTargetType(type);
        ctx.setTargetId(targetId);
        ctx.setDisplayName(displayName);
        ctx.setExpiresAtMillis(System.currentTimeMillis() + 15 * 60 * 1000);
        return ctx;
    }

    private void applyBranch(ManagerSession session, Branch branch) {
        session.setBranchId(branch.getId());
        session.setBranchName(branch.getName());
    }

    // --- New Profile Management Methods ---

    @Transactional
    public Optional<String> updateOrganizationEmail(Long organizationId, String newEmail) {
        if (newEmail == null || newEmail.isBlank()) {
            return Optional.of("Email is required.");
        }
        newEmail = newEmail.trim();
        if (!newEmail.contains("@")) {
            return Optional.of("Invalid email format.");
        }
        Organization org = organizationRepo.findById(organizationId).orElse(null);
        if (org == null) {
            return Optional.of("Organization not found.");
        }
        if (organizationRepo.existsByEmailIgnoreCase(newEmail) && !org.getEmail().equalsIgnoreCase(newEmail)) {
            return Optional.of("This email is already registered to another organization.");
        }
        org.setEmail(newEmail);
        organizationRepo.save(org);
        return Optional.empty();
    }

    public boolean hasLocalPayoutDetails(Organization org) {
        return org != null
                && org.getPayoutBankName() != null && !org.getPayoutBankName().isBlank()
                && org.getPayoutAccountNumber() != null && !org.getPayoutAccountNumber().isBlank();
    }

    public boolean isPayoutAccountRegistered(Organization org) {
        if (!hasLocalPayoutDetails(org)) {
            return false;
        }
        // When Stripe Connect is enabled the gate only lifts once the
        // Express account has actually been created on Stripe (the
        // account id is stored on the Organization row). With Connect
        // disabled (demo / local-only mode) the local bank fields alone
        // are enough to consider the payout account "registered" so the
        // profile gate lifts and the user can proceed.
        if (stripeConnectService.isConnectEnabled()) {
            return org.getStripeAccountId() != null && !org.getStripeAccountId().isBlank();
        }
        return true;
    }

    public boolean needsPayoutAccountSetup(Long organizationId) {
        return organizationRepo.findById(organizationId)
                .map(org -> !isPayoutAccountRegistered(org))
                .orElse(true);
    }

    @Transactional
    public Optional<String> updatePayoutBankDetails(Long organizationId, String bankName, String accountNumber) {
        if (bankName == null || bankName.isBlank()) {
            return Optional.of("Bank name is required.");
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            return Optional.of("Account number is required.");
        }
        bankName = bankName.trim();
        accountNumber = accountNumber.trim().replaceAll("\\s+", "");

        // Bank-aware validation: each bank in the catalogue has its own
        // expected account-number length range (see BankCatalog).
        var bankError = BankCatalog.validate(bankName, accountNumber);
        if (bankError.isPresent()) {
            return bankError;
        }

        Organization org = organizationRepo.findById(organizationId).orElse(null);
        if (org == null) {
            return Optional.of("Organization not found.");
        }

        // Save the bank details locally for the "Pay at Counter" display
        // copy. The real, verified bank is held by Stripe and collected
        // via the Express onboarding link (see StripeConnectService).
        org.setPayoutBankName(bankName);
        org.setPayoutAccountNumber(accountNumber);
        organizationRepo.save(org);
        return Optional.empty();
    }

    /**
     * Returns the URL the organization owner should visit in their
     * browser to enter the bank / KYC details with Stripe (Express
     * Connect onboarding).
     */
    public Optional<String> createStripeOnboardingLink(Long organizationId) {
        Organization org = organizationRepo.findById(organizationId).orElse(null);
        if (org == null) return Optional.of("Organization not found.");
        return stripeConnectService.createOnboardingLink(org);
    }

    @Transactional
    public Optional<String> changeOrganizationPassword(Long organizationId, String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            return Optional.of("Current password is required.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            return Optional.of("New password must be at least 6 characters.");
        }
        Organization org = organizationRepo.findById(organizationId).orElse(null);
        if (org == null) {
            return Optional.of("Organization not found.");
        }
        if (!PasswordUtil.matches(currentPassword, org.getPasswordHash())) {
            return Optional.of("Current password is incorrect.");
        }
        org.setPasswordHash(PasswordUtil.hash(newPassword));
        organizationRepo.save(org);
        return Optional.empty();
    }

    /** Sends a password-reset OTP. Returns empty if account not found or email could not be sent. */
    public Optional<String> sendPasswordResetOtp(String resetType, String organizationName,
                                                  String branchName, String email) {
        if (email == null || email.isBlank()) {
            return Optional.of("Email is required.");
        }
        email = email.trim();

        String payload;
        if ("branch".equalsIgnoreCase(resetType)) {
            if (branchName == null || branchName.isBlank() || organizationName == null || organizationName.isBlank()) {
                return Optional.of("Branch name and organization name are required.");
            }
            Organization org = organizationRepo.findByNameIgnoreCase(organizationName.trim()).orElse(null);
            if (org == null || !org.getEmail().equalsIgnoreCase(email)) {
                return Optional.of("No matching branch account found for these details.");
            }
            Branch branch = branchRepo.findByOrganization_IdAndNameIgnoreCase(org.getId(), branchName.trim())
                    .filter(Branch::isSetupComplete)
                    .orElse(null);
            if (branch == null) {
                return Optional.of("No matching branch account found for these details.");
            }
            payload = "BRANCH:" + branch.getId();
        } else {
            if (organizationName == null || organizationName.isBlank()) {
                return Optional.of("Organization name is required.");
            }
            Organization org = organizationRepo.findByNameIgnoreCase(organizationName.trim()).orElse(null);
            if (org == null || !org.getEmail().equalsIgnoreCase(email)) {
                return Optional.of("No matching organization account found for these details.");
            }
            payload = "ORGANIZATION:" + org.getId();
        }

        String code = otpService.createOtp(Purpose.PASSWORD_RESET, email, payload);
        if (!emailService.sendPasswordResetOtp(email, code)) {
            otpService.clear(Purpose.PASSWORD_RESET, email);
            return Optional.of("Could not send verification email. Check email settings in application.properties.");
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<String> resetPasswordWithOtp(String email, String code, String newPassword) {
        if (email == null || email.isBlank()) {
            return Optional.of("Email is required.");
        }
        if (code == null || code.isBlank()) {
            return Optional.of("Verification code is required.");
        }

        var otpEntry = otpService.verify(Purpose.PASSWORD_RESET, email.trim(), code);
        if (otpEntry.isEmpty()) {
            return Optional.of("Invalid or expired verification code.");
        }

        String payload = otpEntry.get().payload();
        String[] parts = payload.split(":", 2);
        if (parts.length != 2) {
            return Optional.of("Invalid reset session. Please start again.");
        }

        String targetType = parts[0];
        Long targetId;
        try {
            targetId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return Optional.of("Invalid reset session. Please start again.");
        }

        int minLen = "BRANCH".equals(targetType) ? 4 : 6;
        if (newPassword == null || newPassword.length() < minLen) {
            return Optional.of("Password must be at least " + minLen + " characters.");
        }

        if ("BRANCH".equals(targetType)) {
            Branch branch = branchRepo.findById(targetId).orElse(null);
            if (branch == null) {
                return Optional.of("Branch not found.");
            }
            branch.setPasswordHash(PasswordUtil.hash(newPassword));
            branch.setEncryptedPassword(PasswordUtil.encrypt(newPassword, cryptoSecret));
            branchRepo.save(branch);
        } else {
            Organization org = organizationRepo.findById(targetId).orElse(null);
            if (org == null) {
                return Optional.of("Organization not found.");
            }
            org.setPasswordHash(PasswordUtil.hash(newPassword));
            organizationRepo.save(org);
        }
        return Optional.empty();
    }

    public void saveRegistrationContext(HttpSession httpSession, RegistrationContext context) {
        httpSession.setAttribute(RegistrationContext.SESSION_KEY, context);
    }

    public RegistrationContext getRegistrationContext(HttpSession httpSession) {
        Object value = httpSession.getAttribute(RegistrationContext.SESSION_KEY);
        return value instanceof RegistrationContext ctx ? ctx : null;
    }

    public void clearRegistrationContext(HttpSession httpSession) {
        httpSession.removeAttribute(RegistrationContext.SESSION_KEY);
    }

    // =====================================================================
    // Legacy single-step overloads
    // These exist so older controllers (ManageAuthController) compile
    // and run without changes. They do NOT remove or replace any of the
    // 2-step OTP / session-based methods above.
    // =====================================================================

    /**
     * Single-step organization registration: creates the Organization
     * immediately (no OTP), marks email as verified, and returns an empty
     * Optional on success or an error message on failure.
     */
    @Transactional
    public Optional<String> register(String orgName, String email, String password) {
        if (orgName == null || orgName.isBlank()) return Optional.of("Organization name is required.");
        if (email == null || email.isBlank()) return Optional.of("Email is required.");
        if (!email.contains("@")) return Optional.of("Please enter a valid email address.");
        if (password == null || password.length() < 6) return Optional.of("Password must be at least 6 characters.");

        String trimmedName = orgName.trim();
        String trimmedEmail = email.trim();

        if (organizationRepo.existsByNameIgnoreCase(trimmedName)) {
            return Optional.of("An organization with this name already exists.");
        }
        if (organizationRepo.existsByEmailIgnoreCase(trimmedEmail)) {
            return Optional.of("This email is already registered.");
        }

        Organization org = new Organization();
        org.setName(trimmedName);
        org.setEmail(trimmedEmail);
        org.setPasswordHash(PasswordUtil.hash(password));
        org.setBranchLimit(10);
        org.setEmailVerified(true);
        organizationRepo.save(org);
        return Optional.empty();
    }

    /**
     * Generates a password-reset OTP for the given account and returns the
     * code as a String (or null if the account does not exist). The caller
     * is responsible for delivering the code to the user (email/console).
     * Stateless variant used by the legacy controller flow.
     */
    public String generatePasswordReset(String resetType, String orgName, String branchName, String email) {
        if (email == null || email.isBlank()) return null;
        String normalizedEmail = email.trim();

        String payload;
        if ("branch".equalsIgnoreCase(resetType)) {
            if (branchName == null || branchName.isBlank() || orgName == null || orgName.isBlank()) {
                return null;
            }
            Organization org = organizationRepo.findByNameIgnoreCase(orgName.trim()).orElse(null);
            if (org == null || !org.getEmail().equalsIgnoreCase(normalizedEmail)) {
                return null;
            }
            Branch branch = branchRepo.findByOrganization_IdAndNameIgnoreCase(org.getId(), branchName.trim())
                    .filter(Branch::isSetupComplete)
                    .orElse(null);
            if (branch == null) {
                return null;
            }
            payload = "BRANCH:" + branch.getId();
        } else {
            if (orgName == null || orgName.isBlank()) return null;
            Organization org = organizationRepo.findByNameIgnoreCase(orgName.trim()).orElse(null);
            if (org == null || !org.getEmail().equalsIgnoreCase(normalizedEmail)) {
                return null;
            }
            payload = "ORGANIZATION:" + org.getId();
        }
        return otpService.createOtp(Purpose.PASSWORD_RESET, normalizedEmail, payload);
    }

    /**
     * Verifies the OTP code for password reset and updates the password for
     * the matching Organization or Branch. Returns an empty Optional on
     * success or an error message on failure. Stateless variant used by the
     * legacy controller flow.
     */
    @Transactional
    public Optional<String> resetPassword(String resetType, String orgName, String branchName,
                                          String email, String code, String newPassword) {
        if (email == null || email.isBlank()) return Optional.of("Email is required.");
        if (code == null || code.isBlank()) return Optional.of("Verification code is required.");

        var otpEntry = otpService.verify(Purpose.PASSWORD_RESET, email.trim(), code);
        if (otpEntry.isEmpty()) {
            return Optional.of("Invalid or expired verification code.");
        }

        String payload = otpEntry.get().payload();
        String[] parts = payload.split(":", 2);
        if (parts.length != 2) {
            return Optional.of("Invalid reset session. Please start again.");
        }
        String targetType = parts[0];
        Long targetId;
        try {
            targetId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return Optional.of("Invalid reset session. Please start again.");
        }

        int minLen = "BRANCH".equals(targetType) ? 4 : 6;
        if (newPassword == null || newPassword.length() < minLen) {
            return Optional.of("Password must be at least " + minLen + " characters.");
        }

        if ("BRANCH".equals(targetType)) {
            Branch branch = branchRepo.findById(targetId).orElse(null);
            if (branch == null) return Optional.of("Branch not found.");
            branch.setPasswordHash(PasswordUtil.hash(newPassword));
            branch.setEncryptedPassword(PasswordUtil.encrypt(newPassword, cryptoSecret));
            branchRepo.save(branch);
        } else {
            Organization org = organizationRepo.findById(targetId).orElse(null);
            if (org == null) return Optional.of("Account not found.");
            org.setPasswordHash(PasswordUtil.hash(newPassword));
            organizationRepo.save(org);
        }
        return Optional.empty();
    }
}
