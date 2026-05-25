package com.example.menumanager.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.menumanager.model.Branch;
import com.example.menumanager.repository.BranchRepository;
import com.example.menumanager.repository.OrganizationRepository;
import com.example.menumanager.service.ManagerAuthService;
import com.example.menumanager.service.QrCodeService;
import com.example.menumanager.session.ManagerSession;

import jakarta.servlet.http.HttpSession;

@Controller
public class BranchQrController {

    @Autowired private BranchRepository branchRepo;
    @Autowired private OrganizationRepository organizationRepo;
    @Autowired private ManagerAuthService authService;
    @Autowired private QrCodeService qrCodeService;

    @GetMapping("/manage/qr/{branchId}")
    @Transactional(readOnly = true)
    public String qrCodesPage(@PathVariable Long branchId, HttpSession httpSession, Model model) {
        ManagerSession session = authService.getSession(httpSession);
        if (session == null) {
            return "redirect:/manage/login";
        }

        Branch branch = branchRepo.findById(branchId).orElse(null);
        if (branch == null || !branch.getOrganization().getId().equals(session.getOrganizationId())) {
            return "redirect:/manage";
        }

        List<String> customerUrls = new ArrayList<>();
        for (int t = 1; t <= branch.getTableCount(); t++) {
            customerUrls.add(qrCodeService.buildCustomerTableUrl(branchId, t));
        }

        long configured = authService.getBranchesForOrganization(session.getOrganizationId()).size();
        var org = organizationRepo.findById(session.getOrganizationId()).orElse(null);
        boolean canAddBranch = org != null && configured < org.getBranchLimit();

        model.addAttribute("branch", branch);
        model.addAttribute("tableCount", branch.getTableCount());
        model.addAttribute("customerUrls", customerUrls);
        model.addAttribute("canAddBranch", canAddBranch);
        return "manage-qr";
    }

    @GetMapping(value = "/manage/qr/{branchId}/image", produces = MediaType.IMAGE_PNG_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> qrImage(
            @PathVariable Long branchId,
            @RequestParam int table,
            HttpSession httpSession
    ) {
        ManagerSession session = authService.getSession(httpSession);
        if (session == null) {
            return ResponseEntity.status(401).build();
        }

        Branch branch = branchRepo.findById(branchId).orElse(null);
        if (branch == null || !branch.getOrganization().getId().equals(session.getOrganizationId())) {
            return ResponseEntity.notFound().build();
        }
        if (table < 1 || table > branch.getTableCount()) {
            return ResponseEntity.badRequest().build();
        }

        String url = qrCodeService.buildCustomerTableUrl(branchId, table);
        byte[] png = qrCodeService.generatePng(url, 280);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(png);
    }
}
