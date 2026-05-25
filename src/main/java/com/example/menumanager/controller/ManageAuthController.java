package com.example.menumanager.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.menumanager.model.Branch;
import com.example.menumanager.repository.OrganizationRepository;
import com.example.menumanager.service.ManagerAuthService;
import com.example.menumanager.session.ManagerSession;

import jakarta.servlet.http.HttpSession;

@Controller
public class ManageAuthController {

    @Autowired private ManagerAuthService authService;
    @Autowired private OrganizationRepository organizationRepo;

    @GetMapping("/manage/login")
    public String loginPage() {
        return "manage-login";
    }

    @PostMapping("/manage/register")
    public String register(
            @RequestParam String organizationName,
            @RequestParam String email,
            @RequestParam int branchNumber,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes
    ) {
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match.");
            redirectAttributes.addFlashAttribute("activeTab", "register");
            return "redirect:/manage/login";
        }
        var error = authService.register(organizationName, email, branchNumber, password);
        if (error.isPresent()) {
            redirectAttributes.addFlashAttribute("error", error.get());
            redirectAttributes.addFlashAttribute("activeTab", "register");
            return "redirect:/manage/login";
        }
        redirectAttributes.addFlashAttribute("success", "Registration successful. Please log in.");
        return "redirect:/manage/login";
    }

    @PostMapping("/manage/login")
    public String login(
            @RequestParam String accountName,
            @RequestParam String password,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        var sessionOpt = authService.login(accountName, password);

        if (sessionOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Invalid organization or branch name, or password.");
            redirectAttributes.addFlashAttribute("activeTab", "login");
            redirectAttributes.addFlashAttribute("accountName", accountName);
            return "redirect:/manage/login";
        }

        ManagerSession managerSession = sessionOpt.get();
        authService.saveSession(httpSession, managerSession);

        if (authService.needsBranchSetup(managerSession)) {
            return "redirect:/manage/setup";
        }
        if (managerSession.isOrganizationLevel()) {
            return "redirect:/staff";
        }
        return "redirect:/manage";
    }

    @GetMapping("/manage/logout")
    public String logout(HttpSession httpSession) {
        authService.clearSession(httpSession);
        return "redirect:/manage/login";
    }

    @GetMapping("/manage/setup")
    public String setupPage(HttpSession httpSession, Model model) {
        ManagerSession session = authService.getSession(httpSession);
        if (session == null) {
            return "redirect:/manage/login";
        }
        if (!session.isOrganizationLevel()) {
            return "redirect:/staff";
        }

        var org = organizationRepo.findById(session.getOrganizationId()).orElse(null);
        if (org == null) {
            return "redirect:/manage/login";
        }

        long configured = authService.getBranchesForOrganization(session.getOrganizationId()).size();
        if (configured >= org.getBranchLimit()) {
            return "redirect:/manage";
        }
        model.addAttribute("organizationName", org.getName());
        model.addAttribute("branchesConfigured", configured);
        model.addAttribute("branchLimit", org.getBranchLimit());
        model.addAttribute("branchNumber", configured + 1);
        return "manage-setup";
    }

    @PostMapping("/manage/setup")
    public String submitSetup(
            @RequestParam String branchName,
            @RequestParam String location,
            @RequestParam int tableCount,
            @RequestParam String password,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        ManagerSession session = authService.getSession(httpSession);
        if (session == null || !session.isOrganizationLevel()) {
            return "redirect:/manage/login";
        }

        var result = authService.setupBranch(
                session.getOrganizationId(), branchName, location, tableCount, password);
        if (result.error().isPresent()) {
            redirectAttributes.addFlashAttribute("error", result.error().get());
            return "redirect:/manage/setup";
        }

        Long branchId = result.branchId().orElse(null);
        if (branchId != null) {
            authService.switchBranch(session, branchId);
        }
        authService.saveSession(httpSession, session);

        if (branchId != null) {
            return "redirect:/manage/qr/" + branchId;
        }
        return "redirect:/staff";
    }

    @PostMapping("/manage/branch/switch")
    public String switchBranch(
            @RequestParam Long branchId,
            HttpSession httpSession
    ) {
        ManagerSession session = authService.getSession(httpSession);
        if (session != null && session.isOrganizationLevel()) {
            authService.switchBranch(session, branchId);
            authService.saveSession(httpSession, session);
        }
        return "redirect:/staff";
    }
}
