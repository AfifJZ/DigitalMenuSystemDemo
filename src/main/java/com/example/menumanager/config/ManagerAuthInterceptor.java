package com.example.menumanager.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.menumanager.service.ManagerAuthService;
import com.example.menumanager.session.ManagerSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class ManagerAuthInterceptor implements HandlerInterceptor {

    @Autowired private ManagerAuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            return true;
        }

        HttpSession session = request.getSession();
        ManagerSession manager = authService.getSession(session);
        if (manager == null) {
            response.sendRedirect("/manage/login");
            return false;
        }

        authService.ensureBranchSelected(manager);
        authService.saveSession(session, manager);

        if (authService.needsBranchSetup(manager) && !isSetupExemptPath(path)) {
            response.sendRedirect("/manage/setup");
            return false;
        }

        if (!manager.isOrganizationLevel()) {
            if (path.startsWith("/manage/branch/switch")) {
                response.sendRedirect("/manage");
                return false;
            }
            if (path.startsWith("/manage/branches") || path.matches("/manage/branch/\\d+.*")) {
                response.sendRedirect("/staff");
                return false;
            }
        }

        return true;
    }

    private boolean isPublicPath(String path) {
        return path.equals("/manage/login")
                || path.startsWith("/manage/forgot-password")
                || path.startsWith("/manage/reset-password");
    }

    private boolean isSetupExemptPath(String path) {
        return path.startsWith("/manage/setup")
                || path.startsWith("/manage/branches")
                || path.matches("/manage/branch/\\d+.*");
    }
}
