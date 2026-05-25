package com.example.menumanager.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.menumanager.model.Branch;
import com.example.menumanager.repository.BranchRepository;
import com.example.menumanager.service.ManagerAuthService;
import com.example.menumanager.session.ManagerSession;

import jakarta.servlet.http.HttpSession;

@Controller
public class BranchManageController {

    @Autowired private ManagerAuthService authService;
    @Autowired private BranchRepository branchRepo;

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
        }
        return "redirect:/manage/branch/" + branchId;
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
