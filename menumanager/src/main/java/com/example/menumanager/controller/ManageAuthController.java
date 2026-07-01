package com.example.menumanager.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.menumanager.repository.OrganizationRepository;
import com.example.menumanager.service.BankCatalog;
import com.example.menumanager.service.EmailService;
import com.example.menumanager.service.ManagerAuthService;
import com.example.menumanager.session.ManagerSession;

import jakarta.servlet.http.HttpSession;

@Controller
public class ManageAuthController {

    @Autowired private ManagerAuthService authService;
    @Autowired private OrganizationRepository organizationRepo;
    @Autowired private EmailService emailService;

    @GetMapping("/manage/login")
    public String loginPage() {
        return "manage-login";
    }

    @PostMapping("/manage/register")
    public String register(
            @RequestParam String organizationName,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match.");
            redirectAttributes.addFlashAttribute("activeTab", "register");
            return "redirect:/manage/login";
        }
        // Use single-step registration (no OTP)
        var error = authService.register(organizationName, email, password);
        if (error.isPresent()) {
            redirectAttributes.addFlashAttribute("error", error.get());
            redirectAttributes.addFlashAttribute("activeTab", "register");
            return "redirect:/manage/login";
        }
        redirectAttributes.addFlashAttribute("success", "Registration complete. Please log in.");
        return "redirect:/manage/login";
    }

    @GetMapping("/manage/verify-email")
    public String verifyEmailPage(HttpSession httpSession, Model model) {
        // OTP verification removed; redirect to login.
        return "redirect:/manage/login";
    }

    @PostMapping("/manage/verify-email")
    public String verifyEmail(
            @RequestParam String code,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        // Verification endpoint retained for compatibility but no longer used.
        return "redirect:/manage/login";
    }

    @PostMapping("/manage/verify-email/resend")
    public String resendVerificationOtp(HttpSession httpSession, RedirectAttributes redirectAttributes) {
        // No-op since OTP flow removed.
        redirectAttributes.addFlashAttribute("info", "Email verification has been disabled.");
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

        // Removed mandatory branch setup check - allow organization owners to proceed without branches
        if (managerSession.isOrganizationLevel()) {
            return "redirect:/manage/branches";
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
        model.addAttribute("organizationName", org.getName());
        model.addAttribute("branchesConfigured", configured);
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
            @RequestParam(required = false) String redirect,
            HttpSession httpSession
    ) {
        ManagerSession session = authService.getSession(httpSession);
        if (session != null && session.isOrganizationLevel()) {
            authService.switchBranch(session, branchId);
            authService.saveSession(httpSession, session);
        }
        if (redirect != null && !redirect.isBlank() && redirect.startsWith("/manage/")) {
            return "redirect:" + redirect;
        }
        return "redirect:/manage/branches";
    }

    @GetMapping("/manage/profile")
    public String profilePage(HttpSession httpSession, Model model) {
        ManagerSession session = authService.getSession(httpSession);
        if (session == null || !session.isOrganizationLevel()) {
            return "redirect:/manage/login";
        }
        var org = organizationRepo.findById(session.getOrganizationId()).orElse(null);
        if (org == null) {
            return "redirect:/manage/login";
        }
        model.addAttribute("organization", org);
        long branchCount = authService.getBranchesForOrganization(session.getOrganizationId()).size();
        model.addAttribute("branchCount", branchCount);
        // Payout status flag used by manage-profile.html (no longer enforced
        // as a gate; the rest of the app is fully usable without a bank).
        model.addAttribute("payoutAccountRegistered", authService.isPayoutAccountRegistered(org));
        // Bank catalogue used to render the dropdown + dynamic length hint
        model.addAttribute("bankCatalog", BankCatalog.all());
        BankCatalog.BankSpec currentBank = org.getPayoutBankName() == null
                ? null
                : BankCatalog.find(org.getPayoutBankName());
        if (currentBank == null && org.getPayoutAccountNumber() != null) {
            currentBank = BankCatalog.fallback();
        }
        model.addAttribute("currentBank", currentBank);
        return "manage-profile";
    }

    @PostMapping("/manage/profile/update-payout-bank")
    public String updatePayoutBank(
            @RequestParam String bankName,
            @RequestParam String accountNumber,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        ManagerSession session = authService.getSession(httpSession);
        if (session == null || !session.isOrganizationLevel()) {
            return "redirect:/manage/login";
        }
        var error = authService.updatePayoutBankDetails(session.getOrganizationId(), bankName, accountNumber);
        if (error.isPresent()) {
            redirectAttributes.addFlashAttribute("error", error.get());
        } else {
            redirectAttributes.addFlashAttribute("success",
                    "Bank details saved. Now click 'Connect with Stripe' to finish onboarding.");
        }
        return "redirect:/manage/profile";
    }

    /**
     * Starts the Stripe Express Connect onboarding flow for this
     * organization. Generates a one-time hosted URL and redirects the
     * browser to it.
     */
    @GetMapping("/manage/profile/connect-stripe")
    public String connectStripe(HttpSession httpSession, RedirectAttributes redirectAttributes) {
        ManagerSession session = authService.getSession(httpSession);
        if (session == null || !session.isOrganizationLevel()) {
            return "redirect:/manage/login";
        }
        var linkOrError = authService.createStripeOnboardingLink(session.getOrganizationId());
        if (linkOrError.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Could not create Stripe onboarding link. Check the application logs.");
            return "redirect:/manage/profile";
        }
        return "redirect:" + linkOrError.get();
    }

    @PostMapping("/manage/profile/update-email")
    public String updateEmail(
            @RequestParam String newEmail,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        ManagerSession session = authService.getSession(httpSession);
        if (session == null || !session.isOrganizationLevel()) {
            return "redirect:/manage/login";
        }
        var error = authService.updateOrganizationEmail(session.getOrganizationId(), newEmail);
        if (error.isPresent()) {
            redirectAttributes.addFlashAttribute("error", error.get());
        } else {
            redirectAttributes.addFlashAttribute("success", "Email updated successfully.");
        }
        return "redirect:/manage/profile";
    }

    @PostMapping("/manage/profile/change-password")
    public String changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        ManagerSession session = authService.getSession(httpSession);
        if (session == null || !session.isOrganizationLevel()) {
            return "redirect:/manage/login";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords do not match.");
            return "redirect:/manage/profile";
        }
        var error = authService.changeOrganizationPassword(session.getOrganizationId(), currentPassword, newPassword);
        if (error.isPresent()) {
            redirectAttributes.addFlashAttribute("error", error.get());
        } else {
            redirectAttributes.addFlashAttribute("success", "Password changed successfully.");
        }
        return "redirect:/manage/profile";
    }

    @GetMapping("/manage/forgot-password")
    public String forgotPasswordPage() {
        return "manage-forgot-password";
    }

    @PostMapping("/manage/forgot-password")
    public String submitForgotPassword(
            @RequestParam String resetType,
            @RequestParam(required = false) String organizationName,
            @RequestParam(required = false) String branchName,
            @RequestParam String email,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match.");
            redirectAttributes.addFlashAttribute("resetType", resetType);
            redirectAttributes.addFlashAttribute("organizationName", organizationName);
            redirectAttributes.addFlashAttribute("branchName", branchName);
            redirectAttributes.addFlashAttribute("email", email);
            return "redirect:/manage/forgot-password";
        }
        var error = authService.resetPasswordDirect(resetType, organizationName, branchName, email, newPassword);
        if (error.isPresent()) {
            redirectAttributes.addFlashAttribute("error", error.get());
            redirectAttributes.addFlashAttribute("resetType", resetType);
            redirectAttributes.addFlashAttribute("organizationName", organizationName);
            redirectAttributes.addFlashAttribute("branchName", branchName);
            redirectAttributes.addFlashAttribute("email", email);
            return "redirect:/manage/forgot-password";
        }
        redirectAttributes.addFlashAttribute("success", "Password reset successfully. Please log in.");
        return "redirect:/manage/login";
    }

    @PostMapping("/manage/reset-password")
    public String resetPassword(
            @RequestParam String resetType,
            @RequestParam(required = false) String organizationName,
            @RequestParam(required = false) String branchName,
            @RequestParam String email,
            @RequestParam String code,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes
    ) {
        // Legacy endpoint kept for compatibility but OTP verification removed.
        return "redirect:/manage/login";
    }
}


