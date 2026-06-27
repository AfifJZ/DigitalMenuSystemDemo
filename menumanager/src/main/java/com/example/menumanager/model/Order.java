package com.example.menumanager.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Order entity. The previous version had a unidirectional
 * {@code @OneToMany} over a {@code @JoinTable(name = "orders_items")}.
 * That mapping made JPA eagerly join into {@code orders_items} on every
 * single Order load, which 500'd the customer page (and several other
 * pages) whenever that join table had a row with a stale or missing
 * {@code items_id} foreign key.
 *
 * <p>The relationship has been removed entirely. Order items are now
 * stored in the {@code order_item} table and linked back to the order
 * via {@code order_id}. To read the items of an order, use
 * {@code OrderItemRepository.findByOrderId(orderId)} — the customer
 * page never needs to touch them, so the relationship is not declared
 * here at all.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime orderTime = LocalDateTime.now();
    private Double totalAmount;
    private String orderNumber;

    private Long branchId;
    private Integer tableNumber;

    private String status = "KITCHEN"; // Default status
    private String staffNote;

    // --- Online payment tracking (Stripe) ---
    @Column(length = 255)
    private String stripeSessionId;

    @Column(length = 255)
    private String stripePaymentIntentId;

    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;

    public Order() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getOrderTime() { return orderTime; }
    public void setOrderTime(LocalDateTime orderTime) { this.orderTime = orderTime; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Integer getTableNumber() { return tableNumber; }
    public void setTableNumber(Integer tableNumber) { this.tableNumber = tableNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStaffNote() { return staffNote; }
    public void setStaffNote(String staffNote) { this.staffNote = staffNote; }
    public String getStripeSessionId() { return stripeSessionId; }
    public void setStripeSessionId(String stripeSessionId) { this.stripeSessionId = stripeSessionId; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public void setStripePaymentIntentId(String stripePaymentIntentId) { this.stripePaymentIntentId = stripePaymentIntentId; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
}
