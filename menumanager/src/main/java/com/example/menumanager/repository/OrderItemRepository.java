package com.example.menumanager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.menumanager.model.OrderItem;

/**
 * Replaces the old @OneToMany from Order. Items are loaded explicitly
 * via {@link #findByOrderId(Long)} so the customer page never pays the
 * cost of the eager join.
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);
}
