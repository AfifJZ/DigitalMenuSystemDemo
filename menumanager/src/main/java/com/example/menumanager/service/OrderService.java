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
import org.springframework.transaction.annotation.Transactional;

import com.example.menumanager.model.Branch;
import com.example.menumanager.model.Order;
import com.example.menumanager.model.OrderItem;
import com.example.menumanager.model.PublicMenu;
import com.example.menumanager.repository.BranchRepository;
import com.example.menumanager.repository.OrderItemRepository;
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
    private OrderItemRepository orderItemRepo;

    @Autowired
    private BranchRepository branchRepo;

    @Autowired
    private PublicMenuRepository publicMenuRepo;

    @Value("${stripe.secretKey:}")
    private String stripeSecretKey;

    /**
     * Statuses that represent "the customer has paid for this order",
     * regardless of where the order sits in the kitchen pipeline.
     * The dashboard counts these as revenue / best-selling / peak-hour.
     * Previously the filter was only KITCHEN + COMPLETED, which silently
     * excluded all freshly-placed orders (UPFRONT_PAYMENT for counter,
     * ONLINE_PAYMENT_PENDING for online) — so a brand-new order never
     * showed up on the dashboard until staff manually pressed the
     * "Confirm Paid & Send to Kitchen" button on the billing page.
     */
    private static final java.util.Set<String> PAID_STATUSES = java.util.Set.of(
            "UPFRONT_PAYMENT", "ONLINE_PAYMENT_PENDING", "KITCHEN", "COMPLETED"
    );

    /**
     * Cancel counter-payment orders if not confirmed within 3 minutes.
     * Runs every 30 seconds. Only touches Order fields — no items
     * collection, so no risk of triggering the old eager-join 500.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void autoCancelUnpaidCounterOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(3);

        List<Order> unpaid = orderRepo.findAll().stream()
                .filter(o -> "UPFRONT_PAYMENT".equals(o.getStatus()))
                .filter(o -> o.getOrderTime() != null && o.getOrderTime().isBefore(cutoff))
                .toList();

        for (Order order : unpaid) {
            order.setStatus("CANCELLED");
            String note = order.getStaffNote();
            if (note == null || note.isBlank()) {
                order.setStaffNote("Auto-cancelled: not paid at counter within 3 minutes.");
            } else if (!note.contains("Auto-cancelled")) {
                order.setStaffNote(note + " | Auto-cancelled: not paid at counter within 3 minutes.");
            }
            orderRepo.save(order);
        }
    }

    @Transactional
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

        // Save the Order first so it has an ID, then save each item
        // with order_id pointing back to it. The old @OneToMany is gone.
        double total = 0.0;
        List<OrderItem> items = new ArrayList<>();
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
        newOrder.setTotalAmount(total);
        Order saved = orderRepo.save(newOrder);

        for (OrderItem item : items) {
            item.setOrderId(saved.getId());
        }
        orderItemRepo.saveAll(items);

        return saved;
    }

    /**
     * Retrieves all orders waiting for payment and calculates the total if missing.
     */
    public List<Order> getOrdersForBilling(Long branchId) {
        List<Order> allOrders = orderRepo.findAll(Sort.by(Sort.Direction.ASC, "orderTime"));
        List<Order> billingOrders = allOrders.stream()
                .filter(o -> "PAYMENT".equals(o.getStatus()) || "UPFRONT_PAYMENT".equals(o.getStatus()))
                .filter(o -> branchId == null || branchId.equals(o.getBranchId()))
                .collect(Collectors.toList());

        for (Order order : billingOrders) {
            if (order.getTotalAmount() == null) {
                List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());
                double calculatedTotal = items.stream()
                        .mapToDouble(item -> item.getPrice() * item.getQuantity())
                        .sum();
                order.setTotalAmount(calculatedTotal);
                orderRepo.save(order);
            }
        }
        return billingOrders;
    }

    /**
     * Convenience no-arg overload: returns orders waiting for payment across
     * ALL branches.
     */
    public List<Order> getOrdersForBilling() {
        return getOrdersForBilling(null);
    }

    public List<Order> getTodayBillingHistory(Long branchId) {
        return orderRepo.findAll(Sort.by(Sort.Direction.DESC, "orderTime")).stream()
                .filter(o -> o.getOrderTime() != null && o.getOrderTime().toLocalDate().equals(LocalDate.now()))
                .filter(o -> List.of("KITCHEN", "COMPLETED", "REFUNDED").contains(o.getStatus()))
                .filter(o -> branchId == null || branchId.equals(o.getBranchId()))
                .toList();
    }

    public double sumRevenueExcludingRefunds(List<Order> orders) {
        return orders.stream()
                .filter(o -> !"REFUNDED".equals(o.getStatus()))
                .mapToDouble(o -> o.getTotalAmount() == null ? 0.0 : o.getTotalAmount())
                .sum();
    }

    public Map<String, Double> getDailyRevenue() {
        List<Order> completedOrders = orderRepo.findByStatus("COMPLETED", Sort.by(Sort.Direction.ASC, "orderTime"));
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return completedOrders.stream().collect(Collectors.groupingBy(
                order -> order.getOrderTime().format(dtf),
                LinkedHashMap::new,
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
        return getDashboardStats(period, null);
    }

    public DashboardStats getDashboardStats(DashboardPeriod period, Long branchId) {
        DateRange range = dateRange(period);

        List<Order> paidOrders = orderRepo.findAll(Sort.by(Sort.Direction.ASC, "orderTime")).stream()
                .filter(o -> o.getOrderTime() != null)
                .filter(o -> PAID_STATUSES.contains(o.getStatus()))
                .filter(o -> !Objects.equals(o.getStatus(), "REFUNDED"))
                .filter(o -> branchId == null || branchId.equals(o.getBranchId()))
                .filter(o -> !o.getOrderTime().isBefore(range.start))
                .filter(o -> !o.getOrderTime().isAfter(range.end))
                .toList();

        return buildDashboardStats(paidOrders, branchId, null);
    }

    public DashboardStats getDashboardStatsForOrganization(Long organizationId, DashboardPeriod period) {
        DateRange range = dateRange(period);

        List<Branch> branches = branchRepo.findByOrganizationIdOrderByNameAsc(organizationId);
        List<Long> branchIds = branches.stream().map(Branch::getId).toList();

        List<Order> paidOrders = orderRepo.findAll(Sort.by(Sort.Direction.ASC, "orderTime")).stream()
                .filter(o -> o.getOrderTime() != null)
                .filter(o -> PAID_STATUSES.contains(o.getStatus()))
                .filter(o -> !Objects.equals(o.getStatus(), "REFUNDED"))
                .filter(o -> o.getBranchId() == null || branchIds.contains(o.getBranchId()))
                .filter(o -> !o.getOrderTime().isBefore(range.start))
                .filter(o -> !o.getOrderTime().isAfter(range.end))
                .toList();

        return buildDashboardStats(paidOrders, null, branchIds);
    }

    /**
     * Public wrapper around the private {@link #buildDashboardStats}
     * so that the dashboard controller can build the chart payload
     * directly from a list of orders it already fetched (e.g. via
     * {@link #getTodayBillingHistory}, which is the SAME query the
     * Billing & History page uses). This guarantees the dashboard
     * shows exactly the orders the Billing page shows.
     */
    public DashboardStats buildDashboardStatsFromOrders(
            List<Order> paidOrders, Long branchId, List<Long> organizationBranchIds) {
        return buildDashboardStats(paidOrders, branchId, organizationBranchIds);
    }

    private DashboardStats buildDashboardStats(List<Order> paidOrders, Long branchId, List<Long> organizationBranchIds) {
        // Revenue grouped by date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, Double> revenueByDate = paidOrders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getOrderTime().format(dtf),
                        LinkedHashMap::new,
                        Collectors.summingDouble(o -> o.getTotalAmount() == null ? 0.0 : o.getTotalAmount())
                ));

        Map<String, String> nameToCategory = buildNameToCategoryMap(branchId, organizationBranchIds);

        // Load items per order with the new explicit query (was order.getItems())
        Map<Long, List<OrderItem>> itemsByOrder = new HashMap<>();
        for (Order order : paidOrders) {
            itemsByOrder.computeIfAbsent(order.getId(), id -> orderItemRepo.findByOrderId(id));
        }

        Map<String, Long> categoryCounts = new HashMap<>();
        for (Order order : paidOrders) {
            List<OrderItem> items = itemsByOrder.getOrDefault(order.getId(), List.of());
            for (OrderItem item : items) {
                if (item == null || item.getName() == null) continue;
                long qty = item.getQuantity() == null ? 0L : item.getQuantity();
                String key = item.getName().trim().toLowerCase();
                String category = nameToCategory.getOrDefault(key, "Others");
                categoryCounts.merge(category, qty, Long::sum);
            }
        }

        Map<String, Long> bestSellingCategories = categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(8)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<Integer, Long> hourCounts = paidOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getOrderTime().getHour(), Collectors.counting()));

        Map<String, Long> peakHours = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) {
            String label = String.format("%02d:00", h);
            peakHours.put(label, hourCounts.getOrDefault(h, 0L));
        }

        return new DashboardStats(revenueByDate, bestSellingCategories, peakHours);
    }

    private Map<String, String> buildNameToCategoryMap(Long branchId, List<Long> organizationBranchIds) {
        List<PublicMenu> menus;
        if (branchId != null) {
            menus = publicMenuRepo.findByBranchIdOrderByCategoryAscNameAsc(branchId);
        } else if (organizationBranchIds != null && !organizationBranchIds.isEmpty()) {
            menus = organizationBranchIds.stream()
                    .flatMap(id -> publicMenuRepo.findByBranchIdOrderByCategoryAscNameAsc(id).stream())
                    .toList();
        } else {
            menus = publicMenuRepo.findAll();
        }
        return menus.stream()
                .filter(m -> m.getName() != null)
                .collect(Collectors.toMap(
                        m -> m.getName().trim().toLowerCase(),
                        m -> (m.getCategory() == null || m.getCategory().isBlank()) ? "Others" : m.getCategory(),
                        (a, b) -> a
                ));
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

    public boolean orderBelongsToBranch(Long orderId, Long branchId) {
        if (branchId == null || orderId == null) {
            return false;
        }
        return orderRepo.findById(orderId)
                .map(order -> branchId.equals(order.getBranchId()))
                .orElse(false);
    }

    public void updateOrderStatus(Long orderId, String newStatus) {
        orderRepo.findById(orderId).ifPresent(order -> {
            order.setStatus(newStatus);
            orderRepo.save(order);
        });
    }

    public void finalizeOrder(Long orderId, Double finalPrice, String staffNote) {
        orderRepo.findById(orderId).ifPresent(order -> {
            order.setStatus("COMPLETED");
            order.setTotalAmount(finalPrice);
            order.setStaffNote(staffNote);
            orderRepo.save(order);
        });
    }

    public void refundOrder(Long orderId, String staffNote) {
        orderRepo.findById(orderId).ifPresent(order -> {
            if (staffNote != null && !staffNote.isBlank()) {
                order.setStaffNote(staffNote);
            }

            String paymentIntentId = order.getStripePaymentIntentId();
            if (paymentIntentId != null && !paymentIntentId.isBlank()) {
                if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
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
                    order.setStaffNote((order.getStaffNote() == null ? "" : order.getStaffNote()) + " (Stripe refund: " + refund.getId() + ")");
                    orderRepo.save(order);
                } catch (StripeException e) {
                    order.setStaffNote((order.getStaffNote() == null ? "" : order.getStaffNote() + " | ") + "Stripe refund failed: " + e.getMessage());
                    orderRepo.save(order);
                }
                return;
            }

            order.setRefundedAt(LocalDateTime.now());
            order.setStatus("REFUNDED");
            orderRepo.save(order);
        });
    }
}
