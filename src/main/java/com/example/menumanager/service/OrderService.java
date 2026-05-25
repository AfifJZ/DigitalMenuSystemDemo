package com.example.menumanager.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.menumanager.model.Branch;
import com.example.menumanager.model.Order;
import com.example.menumanager.model.OrderItem;
import com.example.menumanager.model.PublicMenu;
import com.example.menumanager.repository.BranchRepository;
import com.example.menumanager.repository.OrderRepository;
import com.example.menumanager.repository.PublicMenuRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private BranchRepository branchRepo;

    @Autowired
    private PublicMenuRepository publicMenuRepo;

    @Value("${stripe.secretKey:}")
    private String stripeSecretKey;

    /**
     * Cancel counter-payment orders if not confirmed within 10 minutes.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30_000)
    public void autoCancelUnpaidCounterOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);

        List<Order> unpaid = orderRepo.findAll().stream()
                .filter(o -> "UPFRONT_PAYMENT".equals(o.getStatus()))
                .filter(o -> o.getOrderTime() != null && o.getOrderTime().isBefore(cutoff))
                .toList();

        for (Order order : unpaid) {
            order.setStatus("CANCELLED");
            String note = order.getStaffNote();
            if (note == null || note.isBlank()) {
                order.setStaffNote("Auto-cancelled: not paid at counter within 10 minutes.");
            } else if (!note.contains("Auto-cancelled")) {
                order.setStaffNote(note + " | Auto-cancelled: not paid at counter within 10 minutes.");
            }
            orderRepo.save(order);
        }
    }

    public Order createOrderFromCart(List<Map<String, Object>> cartItems, String paymentMethod,
                                     Long branchId, Integer tableNumber) {
        Long resolvedBranchId = null;
        Integer resolvedTable = null;
        if (branchId != null || tableNumber != null) {
            if (branchId == null || tableNumber == null) {
                throw new IllegalArgumentException("Invalid table scan. Please scan the QR code again.");
            }
            Branch branch = branchRepo.findById(branchId).orElse(null);
            if (branch == null || !branch.isSetupComplete()
                    || tableNumber < 1 || tableNumber > branch.getTableCount()) {
                throw new IllegalArgumentException("Invalid branch or table number.");
            }
            resolvedBranchId = branchId;
            resolvedTable = tableNumber;
        }

        Order newOrder = new Order();
        newOrder.setOrderTime(LocalDateTime.now());
        if ("COUNTER".equals(paymentMethod)) {
            newOrder.setStatus("UPFRONT_PAYMENT");
        } else {
            newOrder.setStatus("ONLINE_PAYMENT_PENDING");
        }
        if (resolvedBranchId != null) {
            newOrder.setBranchId(resolvedBranchId);
        }
        if (resolvedTable != null) {
            newOrder.setTableNumber(resolvedTable);
        }

        List<OrderItem> items = new ArrayList<>();
        double total = 0.0;
        for (Map<String, Object> cartItem : cartItems) {
            OrderItem item = new OrderItem();
            item.setName(cartItem.get("name").toString());
            item.setPrice(Double.valueOf(cartItem.get("price").toString()));
            item.setQuantity(Integer.parseInt(cartItem.get("quantity").toString()));
            if (cartItem.containsKey("note") && cartItem.get("note") != null) {
                item.setNote(cartItem.get("note").toString());
            }
            total += (item.getPrice() * item.getQuantity());
            items.add(item);
        }
        newOrder.setItems(items);
        newOrder.setTotalAmount(total);
        return orderRepo.save(newOrder);
    }

    /**
     * Retrieves all orders waiting for payment and calculates the total if missing.
     */
    public List<Order> getOrdersForBilling() {
        // Fetch all orders and filter in Java for the two payment stages
        List<Order> allOrders = orderRepo.findAll(Sort.by(Sort.Direction.ASC, "orderTime"));
        List<Order> billingOrders = allOrders.stream()
                .filter(o -> "PAYMENT".equals(o.getStatus()) || "UPFRONT_PAYMENT".equals(o.getStatus()))
                .collect(Collectors.toList());
        
        for (Order order : billingOrders) {
            if (order.getTotalAmount() == null) {
                double calculatedTotal = order.getItems().stream()
                        .mapToDouble(item -> item.getPrice() * item.getQuantity())
                        .sum();
                order.setTotalAmount(calculatedTotal);
                orderRepo.save(order);
            }
        }
        return billingOrders;
    }

    /**
     * Groups completed orders by date to calculate daily revenue.
     */
    public Map<String, Double> getDailyRevenue() {
        List<Order> completedOrders = orderRepo.findByStatus("COMPLETED", Sort.by(Sort.Direction.ASC, "orderTime"));
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Uses Java Streams to group by date and sum the totals
        return completedOrders.stream().collect(Collectors.groupingBy(
                order -> order.getOrderTime().format(dtf),
                LinkedHashMap::new, // Maintains chronological order
                Collectors.summingDouble(Order::getTotalAmount)
        ));
    }

    public enum DashboardPeriod {
        DAILY,
        WEEKLY,
        MONTHLY;

        public static DashboardPeriod fromParam(String raw) {
            if (raw == null) return DAILY;
            return switch (raw.toLowerCase()) {
                case "weekly" -> WEEKLY;
                case "monthly" -> MONTHLY;
                default -> DAILY;
            };
        }
    }

    public record DashboardStats(
            Map<String, Double> revenueByDate,
            Map<String, Long> bestSellingCategories,
            Map<String, Long> peakHours
    ) {}

    public DashboardStats getDashboardStats(DashboardPeriod period) {
        DateRange range = dateRange(period);

        List<Order> paidOrders = orderRepo.findAll(Sort.by(Sort.Direction.ASC, "orderTime")).stream()
                .filter(o -> o.getOrderTime() != null)
                // Treat both "KITCHEN" and "COMPLETED" as paid/valid revenue in this app.
                // (Refunds are excluded.)
                .filter(o -> List.of("KITCHEN", "COMPLETED").contains(o.getStatus()))
                .filter(o -> !Objects.equals(o.getStatus(), "REFUNDED"))
                .filter(o -> !o.getOrderTime().isBefore(range.start))
                .filter(o -> !o.getOrderTime().isAfter(range.end))
                .toList();

        // Revenue grouped by date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, Double> revenueByDate = paidOrders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getOrderTime().format(dtf),
                        LinkedHashMap::new,
                        Collectors.summingDouble(o -> o.getTotalAmount() == null ? 0.0 : o.getTotalAmount())
                ));

        // Build name -> category map (best effort; category can change later, so this is an approximation)
        Map<String, String> nameToCategory = publicMenuRepo.findAll().stream()
                .filter(m -> m.getName() != null)
                .collect(Collectors.toMap(
                        m -> m.getName().trim().toLowerCase(),
                        m -> (m.getCategory() == null || m.getCategory().isBlank()) ? "Others" : m.getCategory(),
                        (a, b) -> a
                ));

        Map<String, Long> categoryCounts = new HashMap<>();
        for (Order order : paidOrders) {
            if (order.getItems() == null) continue;
            order.getItems().forEach(item -> {
                if (item == null || item.getName() == null) return;
                long qty = item.getQuantity();
                String key = item.getName().trim().toLowerCase();
                String category = nameToCategory.getOrDefault(key, "Others");
                categoryCounts.merge(category, qty, Long::sum);
            });
        }

        // Sort categories by quantity desc (top 8)
        Map<String, Long> bestSellingCategories = categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(8)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        // Peak hours: hour(00-23) -> number of orders
        Map<Integer, Long> hourCounts = paidOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getOrderTime().getHour(), Collectors.counting()));

        Map<String, Long> peakHours = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) {
            String label = String.format("%02d:00", h);
            peakHours.put(label, hourCounts.getOrDefault(h, 0L));
        }

        return new DashboardStats(revenueByDate, bestSellingCategories, peakHours);
    }

    private record DateRange(LocalDateTime start, LocalDateTime end) {}

    private DateRange dateRange(DashboardPeriod period) {
        LocalDate today = LocalDate.now();
        return switch (period) {
            case DAILY -> new DateRange(
                    today.atStartOfDay(),
                    today.atTime(LocalTime.MAX)
            );
            case WEEKLY -> {
                LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new DateRange(start.atStartOfDay(), today.atTime(LocalTime.MAX));
            }
            case MONTHLY -> {
                LocalDate start = today.withDayOfMonth(1);
                yield new DateRange(start.atStartOfDay(), today.atTime(LocalTime.MAX));
            }
        };
    }

    /**
     * Centralized method to update order status.
     */
    public void updateOrderStatus(Long orderId, String newStatus) {
        orderRepo.findById(orderId).ifPresent(order -> {
            order.setStatus(newStatus);
            orderRepo.save(order);
        });
    }

    /**
     * Finalizes an order with the final price and any staff notes.
     */
    public void finalizeOrder(Long orderId, Double finalPrice, String staffNote) {
        orderRepo.findById(orderId).ifPresent(order -> {
            order.setStatus("COMPLETED");
            order.setTotalAmount(finalPrice);
            order.setStaffNote(staffNote);
            orderRepo.save(order);
        });
    }

    /**
     * Refunds an order.
     * - If the order has a Stripe PaymentIntent, it will refund via Stripe first.
     * - Otherwise it just marks it REFUNDED in the database (cash/counter flow).
     */
    public void refundOrder(Long orderId, String staffNote) {
        orderRepo.findById(orderId).ifPresent(order -> {
            if (staffNote != null && !staffNote.isBlank()) {
                order.setStaffNote(staffNote);
            }

            String paymentIntentId = order.getStripePaymentIntentId();
            if (paymentIntentId != null && !paymentIntentId.isBlank()) {
                if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
                    // Can't contact Stripe; do not mark refunded.
                    order.setStaffNote((order.getStaffNote() == null ? "" : order.getStaffNote() + " | ") + "Stripe not configured");
                    orderRepo.save(order);
                    return;
                }
                try {
                    Stripe.apiKey = stripeSecretKey;
                    RefundCreateParams params = RefundCreateParams.builder()
                            .setPaymentIntent(paymentIntentId)
                            .build();
                    Refund refund = Refund.create(params);
                    order.setRefundedAt(LocalDateTime.now());
                    order.setStatus("REFUNDED");
                    // Keep refund id in staff note (simple, no new column)
                    order.setStaffNote((order.getStaffNote() == null ? "" : order.getStaffNote()) + " (Stripe refund: " + refund.getId() + ")");
                    orderRepo.save(order);
                } catch (StripeException e) {
                    order.setStaffNote((order.getStaffNote() == null ? "" : order.getStaffNote() + " | ") + "Stripe refund failed: " + e.getMessage());
                    orderRepo.save(order);
                }
                return;
            }

            // Counter / unknown payment: mark refunded internally
            order.setRefundedAt(LocalDateTime.now());
            order.setStatus("REFUNDED");
            orderRepo.save(order);
        });
    }
}