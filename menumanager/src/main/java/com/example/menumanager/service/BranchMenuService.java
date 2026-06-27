package com.example.menumanager.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.menumanager.model.Branch;
import com.example.menumanager.model.PublicMenu;
import com.example.menumanager.repository.BranchRepository;
import com.example.menumanager.repository.PublicMenuRepository;

@Service
public class BranchMenuService {

    @Autowired private PublicMenuRepository menuRepo;
    @Autowired private BranchRepository branchRepo;

    public record MenuImportResult(int imported, int skipped, String error) {
        public boolean hasError() {
            return error != null && !error.isBlank();
        }
    }

    @Transactional(readOnly = true)
    public Optional<Branch> getOwnedBranch(Long branchId, Long organizationId) {
        return branchRepo.findById(branchId)
                .filter(b -> b.getOrganization().getId().equals(organizationId))
                .filter(Branch::isSetupComplete);
    }

    @Transactional(readOnly = true)
    public boolean itemBelongsToBranch(Long itemId, Long branchId) {
        return menuRepo.findById(itemId)
                .map(item -> branchId.equals(item.getBranchId()))
                .orElse(false);
    }

    @Transactional
    public MenuImportResult importMenuItems(Long targetBranchId, Long sourceBranchId,
                                            Long organizationId, List<Long> selectedItemIds,
                                            boolean importAll) {
        Branch target = getOwnedBranch(targetBranchId, organizationId).orElse(null);
        Branch source = getOwnedBranch(sourceBranchId, organizationId).orElse(null);
        if (target == null || source == null) {
            return new MenuImportResult(0, 0,
                    "Import is only allowed between branches in your organization.");
        }
        if (!target.getOrganization().getId().equals(source.getOrganization().getId())) {
            return new MenuImportResult(0, 0,
                    "Source and target branches must belong to the same organization.");
        }
        if (targetBranchId.equals(sourceBranchId)) {
            return new MenuImportResult(0, 0, "Choose a different source branch.");
        }

        List<PublicMenu> sourceItems;
        if (importAll) {
            sourceItems = menuRepo.findByBranchIdOrderByCategoryAscNameAsc(sourceBranchId);
        } else if (selectedItemIds == null || selectedItemIds.isEmpty()) {
            return new MenuImportResult(0, 0, null);
        } else {
            sourceItems = menuRepo.findByBranchIdAndIdIn(sourceBranchId, selectedItemIds);
            if (sourceItems.size() != selectedItemIds.size()) {
                return new MenuImportResult(0, 0,
                        "Some selected items do not belong to the source branch.");
            }
        }

        int imported = 0;
        int skipped = 0;
        for (PublicMenu sourceItem : sourceItems) {
            if (menuRepo.existsByBranchIdAndNameIgnoreCase(targetBranchId, sourceItem.getName())) {
                skipped++;
                continue;
            }
            PublicMenu copy = new PublicMenu();
            copy.setBranchId(targetBranchId);
            copy.setName(sourceItem.getName());
            copy.setDescription(sourceItem.getDescription());
            copy.setPrice(sourceItem.getPrice());
            copy.setCategory(sourceItem.getCategory());
            copy.setImageUrl(sourceItem.getImageUrl());
            copy.setStatus(sourceItem.getStatus() == null ? "AVAILABLE" : sourceItem.getStatus());
            menuRepo.save(copy);
            imported++;
        }
        return new MenuImportResult(imported, skipped, null);
    }

    public List<PublicMenu> listBranchMenu(Long branchId) {
        if (branchId == null) {
            return List.of();
        }
        return menuRepo.findByBranchIdOrderByCategoryAscNameAsc(branchId);
    }

    public List<Long> parseItemIds(List<String> rawIds) {
        List<Long> ids = new ArrayList<>();
        if (rawIds == null) {
            return ids;
        }
        for (String raw : rawIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(raw.trim()));
            } catch (NumberFormatException ignored) {
                // skip invalid
            }
        }
        return ids;
    }
}
