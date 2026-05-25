package com.example.menumanager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.menumanager.model.PublicMenu;

@Repository
public interface PublicMenuRepository extends JpaRepository<PublicMenu, Long> {
}