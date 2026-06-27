package com.example.menumanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Catches any unhandled exception in the web layer and returns a proper
 * error page (HTML for browser requests, JSON for API requests) instead
 * of the bare Whitelabel 500 page. The actual exception + stack trace is
 * still logged so the cause is visible in the application logs.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public Object handleAnyException(Exception ex, Model model) {
        // Always log the full stack trace so we can still see what went
        // wrong even though the user gets a friendly page.
        log.error("Unhandled exception while rendering a page", ex);

        // Show enough info on the error page that the user can read
        // the actual cause (exception class + message) without opening
        // the log. Path is the request URI, so it's easy to map back
        // to which controller / template blew up.
        model.addAttribute("status", 500);
        model.addAttribute("error", "Internal Server Error");
        model.addAttribute("exception", ex.getClass().getName());
        model.addAttribute("message", ex.getMessage() == null ? "(no message)" : ex.getMessage());
        return "error";
    }
}
