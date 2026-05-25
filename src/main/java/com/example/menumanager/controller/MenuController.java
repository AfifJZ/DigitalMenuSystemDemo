package com.example.menumanager.controller;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.menumanager.model.Branch;
import com.example.menumanager.model.Order;
import com.example.menumanager.model.OrderItem;
import com.example.menumanager.model.PublicMenu;
import com.example.menumanager.repository.BranchRepository;
import com.example.menumanager.repository.OrderRepository;
import com.example.menumanager.repository.PublicMenuRepository;
import com.example.menumanager.service.ManagerAuthService;
import com.example.menumanager.service.OrderService;
import com.example.menumanager.service.OrderService.DashboardPeriod;
import com.example.menumanager.session.ManagerSession;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
public class MenuController {

    // Injecting our Data Access and Service layers
    @Autowired private PublicMenuRepository publicMenuRepo;
    @Autowired private OrderRepository orderRepo;
    @Autowired private BranchRepository branchRepo;
    @Autowired private OrderService orderService;
    @Autowired private ManagerAuthService managerAuthService;

    // --- HOME ROUTE ---
    @GetMapping("/")
    public String home() {
        return "redirect:/customer";
    }

    // --- STAFF ROUTES (Managing the Public Menu) ---
    @GetMapping("/staff")
    public String staffPage(Model model) {
        model.addAttribute("menuItems", publicMenuRepo.findAll());
        model.addAttribute("newItem", new PublicMenu());
        return "staff";
    }

    @PostMapping("/staff/save")
    public String saveItem(@ModelAttribute PublicMenu menuItem) {
        publicMenuRepo.save(menuItem);
        return "redirect:/staff";
    }

    @GetMapping("/staff/delete/{id}")
    public String deleteItem(@PathVariable Long id) {
        publicMenuRepo.deleteById(id);
        return "redirect:/staff";
    }

    // --- CUSTOMER ROUTES ---
    @GetMapping("/customer")
    public String customerMenu(
            @RequestParam(required = false) Long branch,
            @RequestParam(required = false) Integer table,
            Model model
    ) {
        List<PublicMenu> items = publicMenuRepo.findAll();
        
        // Group items by category for the UI
        Map<String, List<PublicMenu>> menuByCategory = items.stream()
                .collect(Collectors.groupingBy(item -> item.getCategory() == null ? "Others" : item.getCategory()));
        model.addAttribute("menuByCategory", menuByCategory);

        // Get live queue count for the customer view
        List<Order> kitchenOrders = orderRepo.findByStatus("KITCHEN", Sort.by(Sort.Direction.ASC, "orderTime"));
        model.addAttribute("queueCount", kitchenOrders.size());

        if (branch != null && table != null) {
            branchRepo.findById(branch).ifPresent(b -> {
                if (b.isSetupComplete() && table >= 1 && table <= b.getTableCount()) {
                    model.addAttribute("branchId", b.getId());
                    model.addAttribute("branchName", b.getName());
                    model.addAttribute("tableNumber", table);
                }
            });
        }

        return "customer";
    }

    // --- KITCHEN ROUTES ---
    @GetMapping("/kitchen")
    public String kitchenPage(Model model, HttpSession httpSession) {
        List<Order> allOrders = orderRepo.findAll(Sort.by(Sort.Direction.ASC, "orderTime"));

        Long filterBranchId = null;
        ManagerSession manager = managerAuthService.getSession(httpSession);
        if (manager != null && manager.getBranchId() != null) {
            filterBranchId = manager.getBranchId();
        }
        final Long branchIdFilter = filterBranchId;
        
        // Filter for active kitchen and recently cancelled orders
        List<Order> kitchenView = allOrders.stream()
                .filter(o -> "KITCHEN".equals(o.getStatus()) || "CANCELLED".equals(o.getStatus()) || "REFUND_REQUESTED".equals(o.getStatus()))
                .filter(o -> branchIdFilter == null || branchIdFilter.equals(o.getBranchId()))
                .toList();

        model.addAttribute("orders", kitchenView);
        return "kitchen";
    }

    @GetMapping("/kitchen/complete/{id}")
    public String kitchenComplete(@PathVariable Long id) {
        // Changed from "PAYMENT" to "COMPLETED"
        orderService.updateOrderStatus(id, "COMPLETED"); 
        return "redirect:/kitchen";
    }

    @GetMapping("/kitchen/cancel/{id}")
    public String kitchenCancel(@PathVariable Long id) {
        orderService.updateOrderStatus(id, "CANCELLED");
        return "redirect:/kitchen";
    }

    @GetMapping("/kitchen/dismiss/{id}")
    public String kitchenDismiss(@PathVariable Long id) {
        orderService.updateOrderStatus(id, "ARCHIVED");
        return "redirect:/kitchen";
    }

    @GetMapping("/kitchen/refund/approve/{id}")
    public String approveRefund(@PathVariable Long id) {
        orderService.refundOrder(id, null);
        return "redirect:/kitchen";
    }

    @GetMapping("/kitchen/refund/deny/{id}")
    public String denyRefund(@PathVariable Long id) {
        orderService.updateOrderStatus(id, "KITCHEN");
        return "redirect:/kitchen";
    }

    // --- BILLING & MANAGEMENT ROUTES ---
    // --- MERGED BILLING & HISTORY ROUTES ---
    
    @GetMapping("/billing")
    public String billingPage(Model model) {
        // 1. Pending Payments (for the first tab)
        List<Order> pendingOrders = orderService.getOrdersForBilling();
        model.addAttribute("orders", pendingOrders);

        // 2. Today's History (for the second tab)
        List<Order> historyOrders = orderRepo.findAll(Sort.by(Sort.Direction.DESC, "orderTime")).stream()
                .filter(o -> o.getOrderTime().toLocalDate().equals(LocalDate.now()))
                .filter(o -> List.of("KITCHEN", "COMPLETED", "REFUNDED").contains(o.getStatus()))
                .toList();

        // Calculate revenue
        double totalRevenue = historyOrders.stream()
                .filter(o -> !o.getStatus().equals("REFUNDED"))
                .mapToDouble(Order::getTotalAmount)
                .sum();

        model.addAttribute("historyOrders", historyOrders);
        model.addAttribute("totalRevenue", totalRevenue);

        return "billing";
    }

    @PostMapping("/billing/refund")
    public String processRefund(@RequestParam Long orderId, @RequestParam String staffNote) {
        orderService.refundOrder(orderId, staffNote);
        // Redirect back to billing
        return "redirect:/billing";
    }

    @GetMapping("/billing/export")
    public void exportDailyReport(HttpServletResponse response) throws Exception {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"daily_sales_" + LocalDate.now() + ".csv\"");

        PrintWriter writer = response.getWriter();
        writer.println("Order ID,Time,Status,Total Amount (RM),Items,Refund Reason");

        List<Order> historyOrders = orderRepo.findAll(Sort.by(Sort.Direction.DESC, "orderTime")).stream()
                .filter(o -> o.getOrderTime().toLocalDate().equals(LocalDate.now()))
                .filter(o -> List.of("KITCHEN", "COMPLETED", "REFUNDED").contains(o.getStatus()))
                .toList();

        for (Order order : historyOrders) {
            String itemsString = order.getItems().stream()
                    .map(item -> item.getQuantity() + "x " + item.getName())
                    .collect(Collectors.joining(" | "));
            String note = order.getStaffNote() != null ? order.getStaffNote() : "";

            writer.printf("%d,%s,%s,%.2f,\"%s\",\"%s\"\n",
                    order.getId(), order.getOrderTime().toLocalTime().toString(),
                    order.getStatus(), order.getTotalAmount(), itemsString, note);
        }
    }

    @PostMapping("/billing/finalize")
    public String finalizeOrder(@RequestParam Long orderId, 
                                @RequestParam Double finalPrice,
                                @RequestParam String staffNote) {
        orderService.finalizeOrder(orderId, finalPrice, staffNote);
        return "redirect:/billing";
    }

    @GetMapping({"/manage", "/manage/"})
    public String managePage(Model model, @RequestParam(defaultValue = "daily") String period, HttpSession httpSession) {
        ManagerSession manager = managerAuthService.getSession(httpSession);
        if (manager != null && manager.isOrganizationLevel()) {
            return "redirect:/staff";
        }
        DashboardPeriod p = DashboardPeriod.fromParam(period);
        OrderService.DashboardStats stats = orderService.getDashboardStats(p);

        model.addAttribute("period", p.name().toLowerCase());

        model.addAttribute("chartLabels", stats.revenueByDate().keySet());
        model.addAttribute("chartData", stats.revenueByDate().values());

        model.addAttribute("bestCategoryLabels", stats.bestSellingCategories().keySet());
        model.addAttribute("bestCategoryData", stats.bestSellingCategories().values());

        model.addAttribute("peakHourLabels", stats.peakHours().keySet());
        model.addAttribute("peakHourData", stats.peakHours().values());
        return "manage";
    }
    // --- NEW: STAFF ROUTE FOR STATUS UPDATES ---
    @GetMapping("/staff/status/{id}")
    public String updateItemStatus(@PathVariable Long id, @RequestParam String status) {
        publicMenuRepo.findById(id).ifPresent(item -> {
            item.setStatus(status);
            publicMenuRepo.save(item);
        });
        return "redirect:/staff";
    }
    // --- NEW: Route to check how many orders are in the queue ---
    @GetMapping("/api/queue-count")
    @ResponseBody
    public long getQueueCount() {
        // Fetch all orders and count the ones that aren't finished yet
        return orderRepo.findAll().stream()
                .filter(o -> "KITCHEN".equals(o.getStatus()) || "UPFRONT_PAYMENT".equals(o.getStatus()))
                .count();
    }
    // --- UPDATED: Catch Payment Method and set to UPFRONT_PAYMENT ---
    @PostMapping("/api/order")
    @ResponseBody
    public Map<String, Object> receiveCustomerOrder(
            @RequestBody List<Map<String, Object>> cartItems,
            @RequestParam(defaultValue = "COUNTER") String paymentMethod,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Integer tableNumber
    ) {
        var tableContext = resolveTableContext(branchId, tableNumber);
        if (tableContext.error() != null) {
            return Map.of("ok", false, "error", tableContext.error());
        }

        Order newOrder = orderService.createOrderFromCart(
                cartItems, paymentMethod, tableContext.branchId(), tableContext.tableNumber());
        
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("ok", true);
        response.put("orderId", newOrder.getId());
        response.put("status", newOrder.getStatus());
        if (newOrder.getTableNumber() != null) {
            response.put("tableNumber", newOrder.getTableNumber());
        }
        return response;
    }

    @GetMapping("/api/order/{id}")
    @ResponseBody
    public Map<String, Object> getOrder(@PathVariable Long id) {
        return orderRepo.findById(id)
                .map(order -> {
                    Map<String, Object> out = new java.util.LinkedHashMap<>();
                    out.put("ok", true);
                    out.put("id", order.getId());
                    out.put("status", order.getStatus());
                    out.put("orderTime", order.getOrderTime() == null ? "" : order.getOrderTime().toString());
                    out.put("totalAmount", order.getTotalAmount() == null ? 0.0 : order.getTotalAmount());
                    out.put("staffNote", order.getStaffNote() == null ? "" : order.getStaffNote());
                    out.put("tableNumber", order.getTableNumber());
                    out.put("branchId", order.getBranchId());
                    out.put("items", (order.getItems() == null ? List.<OrderItem>of() : order.getItems()).stream().map(this::toItemMap).toList());
                    return out;
                })
                .orElseGet(() -> Map.<String, Object>of("ok", false, "error", "Order not found"));
    }

    @PostMapping("/api/order/{id}/refund-request")
    @ResponseBody
    public Map<String, Object> requestRefund(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reason = body.get("reason") == null ? "" : body.get("reason").toString().trim();
        if (reason.isBlank()) reason = "Customer requested refund";

        final String finalReason = reason;
        return orderRepo.findById(id).map(order -> {
            order.setStatus("REFUND_REQUESTED");
            order.setStaffNote(finalReason);
            orderRepo.save(order);
            return Map.<String, Object>of("ok", true);
        }).orElseGet(() -> Map.<String, Object>of("ok", false, "error", "Order not found"));
    }

    private Map<String, Object> toItemMap(OrderItem item) {
        return Map.of(
                "name", item.getName(),
                "quantity", item.getQuantity(),
                "note", item.getNote()
        );
    }

    private record TableContext(Long branchId, Integer tableNumber, String error) {}

    private TableContext resolveTableContext(Long branchId, Integer tableNumber) {
        if (branchId == null && tableNumber == null) {
            return new TableContext(null, null, null);
        }
        if (branchId == null || tableNumber == null) {
            return new TableContext(null, null, "Invalid table scan. Please scan the QR code again.");
        }
        Branch branch = branchRepo.findById(branchId).orElse(null);
        if (branch == null || !branch.isSetupComplete()) {
            return new TableContext(null, null, "Invalid branch.");
        }
        if (tableNumber < 1 || tableNumber > branch.getTableCount()) {
            return new TableContext(null, null, "Invalid table number.");
        }
        return new TableContext(branchId, tableNumber, null);
    }

    // --- NEW: Staff route to push order from Billing to Kitchen ---
    @PostMapping("/billing/sendToKitchen")
    public String sendToKitchen(@RequestParam Long orderId) {
        orderService.updateOrderStatus(orderId, "KITCHEN");
        return "redirect:/billing";
    }
}