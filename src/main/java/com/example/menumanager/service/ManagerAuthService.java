package com.example.menumanager.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.menumanager.model.Branch;
import com.example.menumanager.model.Organization;
import com.example.menumanager.repository.BranchRepository;
import com.example.menumanager.repository.OrganizationRepository;
import com.example.menumanager.session.ManagerSession;
import com.example.menumanager.session.PasswordResetContext;
import com.example.menumanager.session.PasswordResetContext.TargetType;
import com.example.menumanager.util.PasswordUtil;

import jakarta.servlet.http.HttpSession;

@Service
public class ManagerAuthService {

    @Autowired private OrganizationRepository organizationRepo;
    @Autowired private BranchRepository branchRepo;

    public Optional<String> register(String orgName, String email, int branchLimit, String password) {
        if (orgName == null || orgName.isBlank()) return Optional.of("Organization name is required.");
        if (email == null || email.isBlank()) return Optional.of("Email is required.");
        if (password == null || password.length() < 6) return Optional.of("Password must be at least 6 characters.");
        if (branchLimit < 1) return Optional.of("Branch number must be at least 1.");

        if (organizationRepo.existsByNameIgnoreCase(orgName.trim())) {
            return Optional.of("An organization with this name already exists.");
        }
        if (organizationRepo.existsByEmailIgnoreCase(email.trim())) {
            return Optional.of("This email is already registered.");
        }

        Organization org = new Organization();
        org.setName(orgName.trim());
        org.setEmail(email.trim());
        org.setPasswordHash(PasswordUtil.hash(password));
        org.setBranchLimit(branchLimit);
        organizationRepo.save(org);
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<ManagerSession> loginOrganization(String orgName, String password) {
        if (orgName == null || orgName.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }
        return organizationRepo.findByNameIgnoreCase(orgName.trim())
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

        long existing = branchRepo.countByOrganizationId(organizationId);
        if (existing >= org.getBranchLimit()) {
            return BranchSetupResult.failure("You have reached the maximum number of branches for your organization.");
        }

        if (branchRepo.existsByOrganization_IdAndNameIgnoreCase(organizationId, name.trim())) {
            return BranchSetupResult.failure("A branch with this name already exists in your organization.");
        }

        Branch branch = new Branch();
        branch.setOrganization(org);
        branch.setName(name.trim());
        branch.setLocation(location.trim());
        branch.setTableCount(tableCount);
        branch.setPasswordHash(PasswordUtil.hash(password));
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
        if (location == null || location.isBlank()) {
            return Optional.of("Location is required.");
        }
        if (tableCount < 1 || tableCount > 500) {
            return Optional.of("Number of tables must be between 1 and 500.");
        }
        Branch branch = branchRepo.findById(branchId).orElse(null);
        if (branch == null || !branch.getOrganization().getId().equals(organizationId)) {
            return Optional.of("Branch not found.");
        }
        branch.setLocation(location.trim());
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
}
