package com.example.menumanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Redirects root path (/) to /manage/login
 */
@Controller
public class RootRedirectController {

    @GetMapping("/")
    public RedirectView redirectRootToLogin() {
        return new RedirectView("/manage/login");
    }
}

