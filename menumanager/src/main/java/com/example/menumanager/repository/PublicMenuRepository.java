package com.example.menumanager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.menumanager.model.PublicMenu;

@Repository
public interface PublicMenuRepository extends JpaRepository<PublicMenu, Long> {

    List<PublicMenu> findByBranchIdOrderByCategoryAscNameAsc(Long branchId);

    List<PublicMenu> findByBranchIdAndIdIn(Long branchId, List<Long> ids);

    boolean existsByBranchIdAndNameIgnoreCase(Long branchId, String name);

    long countByBranchId(Long branchId);
}
