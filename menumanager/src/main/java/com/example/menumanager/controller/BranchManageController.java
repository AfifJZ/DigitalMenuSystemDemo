package com.example.menumanager.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.menumanager.model.Branch;
import com.example.menumanager.model.Organization;
import com.example.menumanager.model.PublicMenu;
import com.example.menumanager.repository.BranchRepository;
import com.example.menumanager.repository.OrganizationRepository;
import com.example.menumanager.repository.PublicMenuRepository;
import com.example.menumanager.service.ManagerAuthService;
import com.example.menumanager.session.ManagerSession;
import com.example.menumanager.util.PasswordUtil;

import jakarta.servlet.http.HttpSession;

@Controller
public class BranchManageController {

    @Autowired private ManagerAuthService authService;
    @Autowired private BranchRepository branchRepo;
    @Autowired private OrganizationRepository organizationRepo;
    @Autowired private PublicMenuRepository publicMenuRepo;

    @Value("${app.crypto.secret}")
    private String cryptoSecret;

    @GetMapping("/manage/branches")
    @Transactional(readOnly = true)
    public String branchesList(HttpSession httpSession, Model model) {
        ManagerSession session = requireOrgAdmin(httpSession);
        if (session == null) {
            return "redirect:/manage/login";
        }
        model.addAttribute("branches", authService.getBranchesForOrganization(session.getOrganizationId()));
        return "manage-branches";
    }

    @GetMapping("/manage/branch/{branchId}")
    @Transactional(readOnly = true)
    public String branchDetail(@PathVariable Long branchId, HttpSession httpSession, Model model) {
        ManagerSession session = requireOrgAdmin(httpSession);
        if (session == null) {
            return "redirect:/manage/login";
        }
        Branch branch = loadOwnedBranch(branchId, session.getOrganizationId());
        if (branch == null) {
            return "redirect:/manage/branches";
        }
        model.addAttribute("branch", branch);

        // Load menu items for this branch
        List<PublicMenu> menuItems = publicMenuRepo.findByBranchIdOrderByCategoryAscNameAsc(branchId);
        model.addAttribute("menuItems", menuItems);
        model.addAttribute("newItem", new PublicMenu());
        model.addAttribute("menuItemCount", menuItems.size());
        model.addAttribute("activeBranchId", branchId);

        // Import button: show only if org has more than one branch
        List<Branch> allBranches = authService.getBranchesForOrganization(session.getOrganizationId());
        if (allBranches.size() > 1) {
            model.addAttribute("canImportMenu", true);
            model.addAttribute("importMenuUrl", "/manage/branch/" + branchId + "/menu-import");
        } else {
            model.addAttribute("canImportMenu", false);
        }

        return "manage-branch-detail";
    }

    @PostMapping("/manage/branch/{branchId}/update")
    public String updateBranch(
            @PathVariable Long branchId,
            @RequestParam String location,
            @RequestParam int tableCount,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        ManagerSession session = requireOrgAdmin(httpSession);
        if (session == null) {
            return "redirect:/manage/login";
        }
        var error = authService.updateBranchDetails(branchId, session.getOrganizationId(), location, tableCount);
        if (error.isPresent()) {
            redirectAttributes.addFlashAttribute("error", error.get());
        } else {
            redirectAttributes.addFlashAttribute("success", "Branch details updated.");
            if (session.getBranchId() != null && session.getBranchId().equals(branchId)) {
                authService.getBranch(branchId).ifPresent(b -> {
                    session.setBranchName(b.getName());
                    authService.saveSession(httpSession, session);
                });
            }
        }
        return "redirect:/manage/branch/" + branchId;
    }

    @PostMapping("/manage/branch/{branchId}/update-tables")
    public String updateTableCount(
            @PathVariable Long branchId,
            @RequestParam int tableCount,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        ManagerSession session = requireOrgAdmin(httpSession);
        if (session == null) {
            return "redirect:/manage/login";
        }
        var error = authService.updateBranchDetails(branchId, session.getOrganizationId(), null, tableCount);
        if (error.isPresent()) {
            redirectAttributes.addFlashAttribute("error", error.get());
        } else {
            redirectAttributes.addFlashAttribute("success", "Table count updated.");
        }
        return "redirect:/manage/branches";
    }

    @PostMapping("/manage/branch/{branchId}/delete")
    @Transactional
    public String deleteBranch(
            @PathVariable Long branchId,
            @RequestParam String branchNameConfirm,
            @RequestParam String branchPassword,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        ManagerSession session = requireOrgAdmin(httpSession);
        if (session == null) {
            return "redirect:/manage/login";
        }
        
        Branch branch = loadOwnedBranch(branchId, session.getOrganizationId());
        if (branch == null) {
            redirectAttributes.addFlashAttribute("error", "Branch not found.");
            return "redirect:/manage/branches";
        }

        if (!branch.getName().equals(branchNameConfirm.trim())) {
            redirectAttributes.addFlashAttribute("error", "Branch name does not match.");
            return "redirect:/manage/branches";
        }
        
        // Verify branch password only if encrypted password exists
        if (branch.getEncryptedPassword() != null && !branch.getEncryptedPassword().isBlank()) {
            String decryptedPassword = PasswordUtil.decrypt(branch.getEncryptedPassword(), cryptoSecret);
            if (!decryptedPassword.equals(branchPassword)) {
                redirectAttributes.addFlashAttribute("error", "Incorrect branch password.");
                return "redirect:/manage/branches";
            }
        }
        
        // Delete the branch
        branchRepo.deleteById(branchId);
        
        redirectAttributes.addFlashAttribute("success", "Branch '" + branch.getName() + "' deleted permanently.");
        return "redirect:/manage/branches";
    }

    @PostMapping("/manage/branch/{branchId}/password")
    public String changeBranchPassword(
            @PathVariable Long branchId,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        ManagerSession session = requireOrgAdmin(httpSession);
        if (session == null) {
            return "redirect:/manage/login";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match.");
            return "redirect:/manage/branch/" + branchId;
        }
        var error = authService.updateBranchPassword(branchId, session.getOrganizationId(), newPassword);
        if (error.isPresent()) {
            redirectAttributes.addFlashAttribute("error", error.get());
        } else {
            redirectAttributes.addFlashAttribute("success", "Branch login password updated.");
            redirectAttributes.addFlashAttribute("plainPassword", newPassword);
        }
        return "redirect:/manage/branch/" + branchId;
    }

    /**
     * Reveals the branch password after authenticating the organization admin.
     * The org admin must provide their own password to prove they are authorised.
     */
    @PostMapping("/manage/branch/{branchId}/reveal-password")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> revealBranchPassword(
            @PathVariable Long branchId,
            @RequestParam String orgPassword,
            HttpSession httpSession
    ) {
        ManagerSession session = authService.getSession(httpSession);
        if (session == null || !session.isOrganizationLevel()) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "Unauthorized"));
        }

        Branch branch = loadOwnedBranch(branchId, session.getOrganizationId());
        if (branch == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", "Branch not found"));
        }

        // Verify org admin's password
        Organization org = organizationRepo.findById(session.getOrganizationId()).orElse(null);
        if (org == null || !PasswordUtil.matches(orgPassword, org.getPasswordHash())) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "Organization password is incorrect."));
        }

        // Decrypt the branch password
        if (branch.getEncryptedPassword() == null || branch.getEncryptedPassword().isBlank()) {
            return ResponseEntity.ok(Map.of("ok", false, "error", "No stored password to reveal. Set a new password first."));
        }

        String plainPassword = PasswordUtil.decrypt(branch.getEncryptedPassword(), cryptoSecret);
        return ResponseEntity.ok(Map.of("ok", true, "password", plainPassword));
    }

    private ManagerSession requireOrgAdmin(HttpSession httpSession) {
        ManagerSession session = authService.getSession(httpSession);
        if (session == null || !session.isOrganizationLevel()) {
            return null;
        }
        return session;
    }

    private Branch loadOwnedBranch(Long branchId, Long organizationId) {
        return branchRepo.findById(branchId)
                .filter(b -> b.getOrganization().getId().equals(organizationId))
                .filter(Branch::isSetupComplete)
                .orElse(null);
    }
}
