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

    // Lightweight count used by the customer page header. Avoids loading
    // the eager-fetched @OneToMany items collection (which can fail
    // with 500s when the orders_items join table has orphan rows).
    long countByStatus(String status);
}
