package com.example.menumanager.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.menumanager.service.ManagerAuthService;
import com.example.menumanager.session.PasswordResetContext;

import jakarta.servlet.http.HttpSession;

@Controller
public class ManagePasswordController {

    @Autowired private ManagerAuthService authService;

    @GetMapping("/manage/forgot-password")
    public String forgotPasswordPage() {
        return "manage-forgot-password";
    }

    @PostMapping("/manage/forgot-password")
    public String verifyForgotPassword(
            @RequestParam String resetType,
            @RequestParam(required = false) String organizationName,
            @RequestParam(required = false) String branchName,
            @RequestParam String email,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        var result = authService.startPasswordReset(resetType, organizationName, branchName, email);
        if (result.error().isPresent()) {
            redirectAttributes.addFlashAttribute("error", result.error().get());
            redirectAttributes.addFlashAttribute("resetType", resetType);
            redirectAttributes.addFlashAttribute("organizationName", organizationName);
            redirectAttributes.addFlashAttribute("branchName", branchName);
            redirectAttributes.addFlashAttribute("email", email);
            return "redirect:/manage/forgot-password";
        }
        authService.savePasswordResetContext(httpSession, result.context().get());
        return "redirect:/manage/reset-password";
    }

    @GetMapping("/manage/reset-password")
    public String resetPasswordPage(HttpSession httpSession, Model model, RedirectAttributes redirectAttributes) {
        PasswordResetContext ctx = authService.getPasswordResetContext(httpSession);
        if (ctx == null || ctx.isExpired()) {
            redirectAttributes.addFlashAttribute("error", "Reset session expired. Please try again.");
            return "redirect:/manage/forgot-password";
        }
        model.addAttribute("resetContext", ctx);
        return "manage-reset-password";
    }

    @PostMapping("/manage/reset-password")
    public String submitResetPassword(
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match.");
            return "redirect:/manage/reset-password";
        }
        var error = authService.completePasswordReset(httpSession, newPassword);
        if (error.isPresent()) {
            redirectAttributes.addFlashAttribute("error", error.get());
            return "redirect:/manage/reset-password";
        }
        authService.clearPasswordResetContext(httpSession);
        redirectAttributes.addFlashAttribute("success", "Password updated. You can sign in now.");
        return "redirect:/manage/login";
    }
}
