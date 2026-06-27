package com.example.menumanager.controller;

import java.util.List;
import java.util.Optional;

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
import com.example.menumanager.model.PublicMenu;
import com.example.menumanager.service.BranchMenuService;
import com.example.menumanager.service.BranchMenuService.MenuImportResult;
import com.example.menumanager.service.ManagerAuthService;
import com.example.menumanager.session.ManagerSession;

import jakarta.servlet.http.HttpSession;

/**
 * Lets an organization owner import menu items from one of their other
 * branches into the currently selected branch. Supports two modes:
 *
 * <ul>
 *   <li>Select all items in one click (button "Import all").</li>
 *   <li>Pick specific items via checkboxes, then "Import selected items".</li>
 * </ul>
 *
 * Items with a name that already exists on the target branch are skipped
 * (case-insensitive) so the import is idempotent.
 */
@Controller
public class BranchMenuImportController {

    @Autowired private BranchMenuService branchMenuService;
    @Autowired private ManagerAuthService authService;

    @GetMapping("/manage/branch/{branchId}/menu-import")
    @Transactional(readOnly = true)
    public String showImportPage(
            @PathVariable Long branchId,
            @RequestParam(required = false) Long sourceBranchId,
            HttpSession httpSession,
            Model model
    ) {
        ManagerSession session = requireOrgAdmin(httpSession);
        if (session == null) {
            return "redirect:/manage/login";
        }

        Optional<Branch> targetOpt = branchMenuService.getOwnedBranch(branchId, session.getOrganizationId());
        if (targetOpt.isEmpty()) {
            return "redirect:/manage/branches";
        }
        Branch targetBranch = targetOpt.get();

        // Every other branch in the same org is a valid import source.
        List<Branch> allBranches = new java.util.ArrayList<>();
        for (Branch b : allOrgBranches(session.getOrganizationId())) {
            if (!b.getId().equals(targetBranch.getId())) {
                allBranches.add(b);
            }
        }
        model.addAttribute("targetBranch", targetBranch);
        model.addAttribute("sourceBranches", allBranches);
        model.addAttribute("targetMenuCount", branchMenuService.listBranchMenu(branchId).size());

        if (sourceBranchId != null) {
            Optional<Branch> sourceOpt = branchMenuService.getOwnedBranch(sourceBranchId, session.getOrganizationId());
            if (sourceOpt.isPresent() && !sourceOpt.get().getId().equals(targetBranch.getId())) {
                Branch source = sourceOpt.get();
                List<PublicMenu> items = branchMenuService.listBranchMenu(source.getId());
                model.addAttribute("sourceBranch", source);
                model.addAttribute("sourceMenuItems", items);
                model.addAttribute("selectedSourceBranchId", source.getId());
            }
        }

        return "manage-branch-menu-import";
    }

    @PostMapping("/manage/branch/{branchId}/menu-import")
    public String submitImport(
            @PathVariable Long branchId,
            @RequestParam Long sourceBranchId,
            @RequestParam(name = "importMode", defaultValue = "selected") String importMode,
            @RequestParam(name = "itemIds", required = false) List<String> itemIdsRaw,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes
    ) {
        ManagerSession session = requireOrgAdmin(httpSession);
        if (session == null) {
            return "redirect:/manage/login";
        }

        boolean importAll = "all".equalsIgnoreCase(importMode);
        List<Long> selectedIds = importAll ? null : branchMenuService.parseItemIds(itemIdsRaw);

        MenuImportResult result = branchMenuService.importMenuItems(
                branchId, sourceBranchId, session.getOrganizationId(),
                selectedIds, importAll);

        if (result.hasError()) {
            redirectAttributes.addFlashAttribute("error", result.error());
        } else if (importAll && selectedIds == null && branchMenuService.parseItemIds(itemIdsRaw).isEmpty() && !importAll) {
            // User clicked "Import selected" but checked nothing.
            redirectAttributes.addFlashAttribute("error", "Select at least one item to import.");
        } else {
            String msg = "Imported " + result.imported() + " item(s)";
            if (result.skipped() > 0) {
                msg += ", skipped " + result.skipped() + " duplicate(s)";
            }
            msg += ".";
            redirectAttributes.addFlashAttribute("success", msg);
        }
        return "redirect:/manage/branch/" + branchId + "/menu-import?sourceBranchId=" + sourceBranchId;
    }

    private ManagerSession requireOrgAdmin(HttpSession httpSession) {
        ManagerSession session = (ManagerSession) httpSession.getAttribute(ManagerSession.SESSION_KEY);
        if (session == null || !session.isOrganizationLevel()) {
            return null;
        }
        return session;
    }

    private List<Branch> allOrgBranches(Long organizationId) {
        return new java.util.ArrayList<>(authService.getBranchesForOrganization(organizationId));
    }
}
