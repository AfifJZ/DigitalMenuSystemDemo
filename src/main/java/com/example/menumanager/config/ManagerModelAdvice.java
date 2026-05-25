package com.example.menumanager.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.example.menumanager.model.Branch;
import com.example.menumanager.repository.OrganizationRepository;
import com.example.menumanager.service.ManagerAuthService;
import com.example.menumanager.session.ManagerSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@ControllerAdvice
public class ManagerModelAdvice {

    @Autowired private ManagerAuthService authService;
    @Autowired private OrganizationRepository organizationRepo;

    @ModelAttribute
    public void addManagerContext(HttpServletRequest request, HttpSession httpSession, Model model) {
        String path = request.getRequestURI();
        if (!isAdminPath(path) || path.equals("/manage/login")) {
            return;
        }

        ManagerSession session = authService.getSession(httpSession);
        if (session == null) {
            return;
        }

        model.addAttribute("managerSession", session);
        model.addAttribute("organizationLevel", session.isOrganizationLevel());

        if (session.getBranchId() != null) {
            authService.getBranch(session.getBranchId()).ifPresent(branch -> {
                model.addAttribute("currentBranch", branch);
            });
        }

        if (session.isOrganizationLevel()) {
            List<Branch> branches = authService.getBranchesForOrganization(session.getOrganizationId());
            model.addAttribute("allBranches", branches);
            organizationRepo.findById(session.getOrganizationId()).ifPresent(org -> {
                model.addAttribute("canAddBranch", branches.size() < org.getBranchLimit());
            });
        }
    }

    private boolean isAdminPath(String path) {
        return path.startsWith("/manage") || path.startsWith("/staff")
                || path.startsWith("/kitchen") || path.startsWith("/billing");
    }
}
