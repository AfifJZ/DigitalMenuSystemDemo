package com.example.menumanager.repository;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.menumanager.model.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // Custom query method to find orders by their status and sort them
    List<Order> findByStatus(String status, Sort sort);
}