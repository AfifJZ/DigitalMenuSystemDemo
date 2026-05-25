package com.example.menumanager.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.menumanager.model.Branch;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByOrganizationIdOrderByNameAsc(Long organizationId);
    long countByOrganizationId(Long organizationId);
    Optional<Branch> findByNameIgnoreCase(String name);
    List<Branch> findAllByNameIgnoreCase(String name);
    boolean existsByOrganization_IdAndNameIgnoreCase(Long organizationId, String name);
    Optional<Branch> findByOrganization_IdAndNameIgnoreCase(Long organizationId, String name);
}
