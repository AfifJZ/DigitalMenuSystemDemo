package com.example.menumanager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.menumanager.model.InternalMenu;

@Repository
public interface InternalMenuRepository extends JpaRepository<InternalMenu, Long> {
}