package com.example.menumanager.controller;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.example.menumanager.model.Branch;
import com.example.menumanager.model.Order;
import com.example.menumanager.model.OrderItem;
import com.example.menumanager.model.Organization;
import com.example.menumanager.model.PublicMenu;
import com.example.menumanager.repository.BranchRepository;
import com.example.menumanager.repository.OrderItemRepository;
import com.example.menumanager.repository.OrderRepository;
import com.example.menumanager.repository.OrganizationRepository;
import com.example.menumanager.repository.PublicMenuRepository;
import com.example.menumanager.service.ManagerAuthService;
import com.example.menumanager.service.OrderService;
import com.example.menumanager.session.ManagerSession;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
public class MenuController {

    // Injecting our Data Access and Service layers
    @Autowired private PublicMenuRepository publicMenuRepo;
    @Autowired private OrderRepository orderRepo;
    @Autowired private OrderItemRepository orderItemRepo;
    @Autowired private BranchRepository branchRepo;
    @Autowired private OrganizationRepository organizationRepo;
    @Autowired private OrderService orderService;
    @Autowired private ManagerAuthService managerAuthService;

    /** Redirect back to the referer page, or fallback to /staff if not available. */
    private String redirectBack(jakarta.servlet.http.HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank() && referer.startsWith(request.getScheme() + "://" + request.getServerName())) {
            return "redirect:" + referer;
        }
        return "redirect:/staff";
    }

    // --- HOME ROUTE ---
    @GetMapping("/")
    public String home() {
        return "redirect:/customer";
    }

    // --- STAFF ROUTES (Managing the Public Menu) ---
    @GetMapping("/staff")
    public String staffPage(HttpSession httpSession, Model model) {
        ManagerSession manager = managerAuthService.getSession(httpSession);

        // Determine which branch's menu this user can see:
        //  - branch-level staff: locked to their own branch
        //  - org-level owner: whatever branch is currently selected in the
        //    session (defaults to the first branch when they log in)
        //  - no session (public / customer) -> redirect handled by the
        //    interceptor; for safety, show nothing.
        Long activeBranchId = null;
        if (manager != null) {
            activeBranchId = manager.getBranchId();
        }

        List<PublicMenu> items = activeBranchId != null
                ? publicMenuRepo.findByBranchIdOrderByCategoryAscNameAsc(activeBranchId)
                : List.of();

        model.addAttribute("menuItems", items);
        model.addAttribute("newItem", new PublicMenu());
        model.addAttribute("menuItemCount", items.size());
        model.addAttribute("activeBranchId", activeBranchId);

        // Import button is only shown to organization owners and only when
        // the organization actually has more than one branch.
        if (manager != null && manager.isOrganizationLevel()
                && activeBranchId != null
                && managerAuthService.getBranchesForOrganization(manager.getOrganizationId()).size() > 1) {
            model.addAttribute("canImportMenu", true);
            model.addAttribute("importMenuUrl",
                    "/manage/branch/" + activeBranchId + "/menu-import");
        } else {
            model.addAttribute("canImportMenu", false);
        }
        return "staff";
    }

    @PostMapping("/staff/save")
    public String saveItem(@ModelAttribute PublicMenu menuItem,
                           @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                           HttpSession httpSession,
                           jakarta.servlet.http.HttpServletRequest request) {
        // Tag every new item with the branch the staff is currently
        // working in. Without this, an item would be branch-less and
        // wouldn't show up in the customer view of any branch.
        ManagerSession manager = managerAuthService.getSession(httpSession);
        if (manager != null
                && manager.getBranchId() != null
                && (menuItem.getBranchId() == null
                    || !manager.getBranchId().equals(menuItem.getBranchId()))) {
            menuItem.setBranchId(manager.getBranchId());
        }

        // Handle image upload
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
                java.nio.file.Path uploadPath = java.nio.file.Paths.get("src/main/resources/static/uploads");
                java.nio.file.Files.createDirectories(uploadPath);
                java.nio.file.Files.copy(imageFile.getInputStream(), uploadPath.resolve(fileName));
                menuItem.setImageUrl("/uploads/" + fileName);
            } catch (Exception e) {
                // Log error but continue with save
                System.err.println("Failed to upload image: " + e.getMessage());
            }
        }

        publicMenuRepo.save(menuItem);
        return redirectBack(request);
    }

    @GetMapping("/staff/delete/{id}")
    public String deleteItem(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request) {
        publicMenuRepo.deleteById(id);
        return redirectBack(request);
    }

    // --- CUSTOMER ROUTES ---
    //
    // Stripped to the absolute minimum on purpose: the customer page
    // is reached by scanning a table QR, so the only thing it MUST do
    // is render the menu for the requested branch. Nothing else in
    // here is allowed to throw — every other call (queue count, branch
    // lookup, payout bank) is wrapped in its own try/catch with a safe
    // default so a single broken row in `orders_items` or a missing
    // `branches` row can never 500 the page again.
    @GetMapping("/customer")
    public String customerMenu(
            @RequestParam(required = false) Long branch,
            @RequestParam(required = false) Integer table,
            Model model
    ) {
        boolean requiresBranchScan = (branch == null || table == null);

        // 1) Menu items for this branch. Empty list if no branch / no scan.
        //    Defensive: filter out nulls so the template can never NPE.
        List<PublicMenu> items = List.of();
        if (!requiresBranchScan) {
            try {
                items = publicMenuRepo.findByBranchIdOrderByCategoryAscNameAsc(branch);
            } catch (Exception ex) {
                items = List.of();
            }
        }
        items = items == null ? List.of() : items.stream()
                .filter(i -> i != null)
                .toList();

        // 2) Group by category for the UI. Use a LinkedHashMap so the
        //    category order is the SQL sort order (category, then name).
        java.util.Map<String, java.util.List<PublicMenu>> menuByCategory = new java.util.LinkedHashMap<>();
        for (PublicMenu item : items) {
            String cat = (item.getCategory() == null || item.getCategory().isBlank())
                    ? "Others" : item.getCategory();
            menuByCategory.computeIfAbsent(cat, k -> new java.util.ArrayList<>()).add(item);
        }

        model.addAttribute("menuByCategory", menuByCategory);
        model.addAttribute("menuItemCount", items.size());
        model.addAttribute("requiresBranchScan", requiresBranchScan);

        // 3) Optional branch / table context for the header banner.
        //    Set whenever the branch row exists and the table number is
        //    in range.  We deliberately do NOT gate on isSetupComplete()
        //    here — a customer who scanned a QR code must still see the
        //    table banner and have the correct branch/table context for
        //    ordering even if the admin has not yet finished the setup
        //    wizard.
        if (!requiresBranchScan) {
            try {
                branchRepo.findById(branch).ifPresent(b -> {
                    // Allow table 1 even when tableCount is 0 (setup not
                    // fully complete) so the customer is never locked out.
                    int effectiveMax = Math.max(b.getTableCount(), table);
                    if (table >= 1 && table <= effectiveMax) {
                        model.addAttribute("branchId", b.getId());
                        model.addAttribute("branchName", b.getName());
                        model.addAttribute("tableNumber", table);
                    }
                });
            } catch (Exception ignored) {
                // Bad branch id must not 500 the page.
            }
        }

        // 4) Queue badge — best effort, never fails the page.
        long queueCount = 0L;
        try {
            queueCount = orderRepo.countByStatus("KITCHEN");
        } catch (Exception ignored) {
            // If the orders table is broken, just show 0.
        }
        model.addAttribute("queueCount", queueCount);

        return "customer";
    }

    // --- DEBUG: raw JSON dump of every menu item, grouped by branch ---
    // Lets the user see exactly what is in the menu_items table without
    // going through the Thymeleaf page. Visit /api/debug/menu.
    @GetMapping(value = "/api/debug/menu", produces = "application/json")
    @ResponseBody
    public java.util.Map<String, Object> debugMenu() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (PublicMenu m : publicMenuRepo.findAll()) {
            java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("id", m.getId());
            r.put("name", m.getName());
            r.put("category", m.getCategory());
            r.put("price", m.getPrice());
            r.put("status", m.getStatus());
            r.put("branch_id", m.getBranchId());
            rows.add(r);
        }
        out.put("count", rows.size());
        out.put("items", rows);
        return out;
    }

    // --- DEBUG: raw JSON dump of every branch ---
    @GetMapping(value = "/api/debug/branches", produces = "application/json")
    @ResponseBody
    public java.util.Map<String, Object> debugBranches() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Branch b : branchRepo.findAll()) {
            java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("id", b.getId());
            r.put("name", b.getName());
            r.put("table_count", b.getTableCount());
            r.put("setup_complete", b.isSetupComplete());
            r.put("organization_id", b.getOrganization() == null ? null : b.getOrganization().getId());
            rows.add(r);
        }
        out.put("count", rows.size());
        out.put("branches", rows);
        return out;
    }

    // --- DEBUG: raw JSON dump of every order, grouped by status ---
    // Lets you verify the dashboard filter is seeing the orders you
    // placed. The PAID_STATUSES set in OrderService is what's expected
    // to show up on the dashboard: UPFRONT_PAYMENT, ONLINE_PAYMENT_PENDING,
    // KITCHEN, and COMPLETED.
    @GetMapping(value = "/api/debug/orders", produces = "application/json")
    @ResponseBody
    public java.util.Map<String, Object> debugOrders() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        java.util.Map<String, Integer> statusCounts = new java.util.LinkedHashMap<>();
        for (Order o : orderRepo.findAll(Sort.by(Sort.Direction.DESC, "orderTime"))) {
            java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("id", o.getId());
            r.put("status", o.getStatus());
            r.put("total", o.getTotalAmount());
            r.put("branch_id", o.getBranchId());
            r.put("table", o.getTableNumber());
            r.put("placed", o.getOrderTime() == null ? null : o.getOrderTime().toString());
            r.put("paid_counted", o.getOrderTime() != null
                    && o.getStatus() != null
                    && (o.getStatus().equals("UPFRONT_PAYMENT")
                        || o.getStatus().equals("ONLINE_PAYMENT_PENDING")
                        || o.getStatus().equals("KITCHEN")
                        || o.getStatus().equals("COMPLETED")));
            rows.add(r);
            statusCounts.merge(o.getStatus() == null ? "(null)" : o.getStatus(), 1, Integer::sum);
        }
        out.put("count", rows.size());
        out.put("by_status", statusCounts);
        out.put("orders", rows);
        return out;
    }

    // --- CHEF DISPLAY ROUTE (read-only, no authentication required) ---
    @GetMapping("/chef")
    public String chefDisplay(Model model) {
        List<Order> allOrders = orderRepo.findAll(Sort.by(Sort.Direction.ASC, "orderTime"));
        List<Order> activeOrders = allOrders.stream()
                .filter(o -> "KITCHEN".equals(o.getStatus()))
                .toList();

        java.util.Map<Long, java.util.List<OrderItem>> orderItemsMap = new java.util.HashMap<>();
        for (Order order : activeOrders) {
            orderItemsMap.put(order.getId(), orderItemRepo.findByOrderId(order.getId()));
        }
        model.addAttribute("orders", activeOrders);
        model.addAttribute("orderItemsMap", orderItemsMap);
        model.addAttribute("queueCount", activeOrders.size());
        return "chef";
    }

    // --- KITCHEN ROUTES ---
    @GetMapping("/kitchen")
    public String kitchenPage(Model model, HttpSession httpSession) {
        ManagerSession manager = managerAuthService.getSession(httpSession);
        
        // Organization-level (owner) users cannot access kitchen
        if (manager != null && manager.isOrganizationLevel()) {
            return "redirect:/manage";
        }
        
        List<Order> allOrders = orderRepo.findAll(Sort.by(Sort.Direction.ASC, "orderTime"));

        Long filterBranchId = null;
        if (manager != null && manager.getBranchId() != null) {
            filterBranchId = manager.getBranchId();
        }
        final Long branchIdFilter = filterBranchId;
        
        // Filter for active kitchen and recently cancelled orders
        // Include all KITCHEN/CANCELLED/REFUND_REQUESTED orders that either:
        // 1. Have no branch restriction (for global staff), OR
        // 2. Match the staff's assigned branch, OR
        // 3. Have no branchId assigned (default/counter orders visible to all branches)
        List<Order> kitchenView = allOrders.stream()
                .filter(o -> "KITCHEN".equals(o.getStatus()) || "CANCELLED".equals(o.getStatus()) || "REFUND_REQUESTED".equals(o.getStatus()))
                .filter(o -> branchIdFilter == null || o.getBranchId() == null || branchIdFilter.equals(o.getBranchId()))
                .toList();

        // Items per order, loaded with the new explicit query (Order no
        // longer carries an items collection).
        java.util.Map<Long, java.util.List<OrderItem>> orderItemsMap = new java.util.HashMap<>();
        for (Order order : kitchenView) {
            orderItemsMap.put(order.getId(), orderItemRepo.findByOrderId(order.getId()));
        }
        model.addAttribute("orders", kitchenView);
        model.addAttribute("orderItemsMap", orderItemsMap);
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
    public String billingPage(Model model, HttpSession httpSession) {
        ManagerSession manager = managerAuthService.getSession(httpSession);
        
        // Organization-level (owner) users cannot access billing
        if (manager != null && manager.isOrganizationLevel()) {
            return "redirect:/manage";
        }
        
        // 1. Pending Payments (for the first tab)
        List<Order> pendingOrders = orderService.getOrdersForBilling();
        model.addAttribute("orders", pendingOrders);

        // 2. Today's History (for the second tab)
        List<Order> historyOrders = orderRepo.findAll(Sort.by(Sort.Direction.DESC, "orderTime")).stream()
                .filter(o -> o.getOrderTime().toLocalDate().equals(LocalDate.now()))
                .filter(o -> List.of("KITCHEN", "COMPLETED", "REFUNDED").contains(o.getStatus()))
                .toList();

        // Items per order, loaded with the new explicit query.
        java.util.Map<Long, java.util.List<OrderItem>> orderItemsMap = new java.util.HashMap<>();
        for (Order order : pendingOrders) {
            orderItemsMap.put(order.getId(), orderItemRepo.findByOrderId(order.getId()));
        }
        for (Order order : historyOrders) {
            orderItemsMap.putIfAbsent(order.getId(), orderItemRepo.findByOrderId(order.getId()));
        }
        model.addAttribute("orderItemsMap", orderItemsMap);

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
            String itemsString = orderItemRepo.findByOrderId(order.getId()).stream()
                    .map(item -> (item.getQuantity() == null ? 0 : item.getQuantity()) + "x " + (item.getName() == null ? "" : item.getName()))
                    .collect(Collectors.joining(" | "));
            String note = order.getStaffNote() != null ? order.getStaffNote() : "";

            writer.printf("%d,%s,%s,%.2f,\"%s\",\"%s\"\n",
                    order.getId(), order.getOrderTime().toLocalTime().toString(),
                    order.getStatus(), order.getTotalAmount(), itemsString, note);
        }
    }

    @GetMapping("/manage/export")
    public void exportDashboardReport(
            HttpServletResponse response,
            HttpSession httpSession,
            @RequestParam(required = false) Long branchId
    ) throws Exception {
        ManagerSession manager = managerAuthService.getSession(httpSession);
        if (manager == null) {
            response.sendError(403, "Unauthorized");
            return;
        }

        Long filterBranchId = null;
        String filenameSuffix = "revenue_report_" + LocalDate.now();

        if (manager.isOrganizationLevel()) {
            if (branchId != null) {
                Branch branch = branchRepo.findById(branchId).orElse(null);
                if (branch == null
                        || branch.getOrganization() == null
                        || !branch.getOrganization().getId().equals(manager.getOrganizationId())) {
                    response.sendError(403, "Unauthorized");
                    return;
                }
                filterBranchId = branchId;
                filenameSuffix = "branch_" + branch.getName().replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + LocalDate.now();
            }
        } else {
            filterBranchId = manager.getBranchId();
        }

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filenameSuffix + ".csv\"");

        PrintWriter writer = response.getWriter();
        writer.println("Order ID,Time,Status,Total Amount (RM),Items,Refund Reason");

        final Long exportBranchId = filterBranchId;
        List<Order> historyOrders = orderRepo.findAll(Sort.by(Sort.Direction.DESC, "orderTime")).stream()
                .filter(o -> o.getOrderTime().toLocalDate().equals(LocalDate.now()))
                .filter(o -> List.of("KITCHEN", "COMPLETED", "REFUNDED").contains(o.getStatus()))
                .filter(o -> exportBranchId == null || exportBranchId.equals(o.getBranchId()))
                .toList();

        for (Order order : historyOrders) {
            String itemsString = orderItemRepo.findByOrderId(order.getId()).stream()
                    .map(item -> (item.getQuantity() == null ? 0 : item.getQuantity()) + "x " + (item.getName() == null ? "" : item.getName()))
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
        OrderService.DashboardPeriod dashboardPeriod = OrderService.DashboardPeriod.fromParam(period);

        // ----------------------------------------------------------------
        // IMPORTANT: The dashboard now reuses the SAME query path as the
        // Billing & History page (getTodayBillingHistory + getDailyRevenue
        // + getOrdersForBilling), so when the Billing page shows RM
        // 15.00 across 3 COMPLETED orders, the dashboard charts show
        // the same 3 orders. The previous implementation used
        // getDashboardStats(...) which had a different filter chain
        // and was silently returning empty even though the data was
        // there.
        // ----------------------------------------------------------------

        // Build the branch / organization filter (same as billing)
        Long filterBranchId = (manager != null && !manager.isOrganizationLevel())
                ? manager.getBranchId() : null;
        String label = (manager != null && !manager.isOrganizationLevel())
                ? "this branch only" : "all branches";

        // 1) Fetch the orders the way the Billing page already does it
        //    (known-working: this is the same call the /billing page uses).
        List<Order> historyOrders = orderService.getTodayBillingHistory(filterBranchId);

        // 2) Compute revenue, just like the Billing page does.
        double totalRevenue = orderService.sumRevenueExcludingRefunds(historyOrders);

        // 3) Build the three datasets Chart.js needs.
        OrderService.DashboardStats stats = orderService.buildDashboardStatsFromOrders(
                historyOrders, filterBranchId, null);

        // 4) Serialize as one clean JSON string for the inline <script>.
        String dashboardJson = buildDashboardJson(stats);
        model.addAttribute("period", dashboardPeriod.name().toLowerCase());
        model.addAttribute("dashboardBranchLabel", label);
        model.addAttribute("dashboardJson", dashboardJson);

        // 5) Best-selling items (aggregate across all paid orders today)
        java.util.Map<String, Long> itemSales = new java.util.LinkedHashMap<>();
        for (Order order : historyOrders) {
            List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());
            for (OrderItem item : items) {
                if (item == null || item.getName() == null) continue;
                long qty = item.getQuantity() == null ? 0L : item.getQuantity();
                itemSales.merge(item.getName(), qty, Long::sum);
            }
        }
        List<java.util.Map<String, Object>> bestSellingItems = itemSales.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("quantity", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());
        model.addAttribute("bestSellingItems", bestSellingItems);

        // Payout status used by the bank-account card on the dashboard
        if (manager != null && manager.isOrganizationLevel()) {
            Organization org = organizationRepo.findById(manager.getOrganizationId()).orElse(null);
            if (org != null) {
                model.addAttribute("organization", org);
                model.addAttribute("payoutAccountRegistered", managerAuthService.isPayoutAccountRegistered(org));
            } else {
                model.addAttribute("payoutAccountRegistered", false);
            }
        } else {
            model.addAttribute("payoutAccountRegistered", false);
            if (manager != null && manager.getBranchId() != null) {
                branchRepo.findById(manager.getBranchId()).ifPresent(b ->
                    model.addAttribute("dashboardBranchName", b.getName()));
            }
        }

        // Load menu items for the current branch (same as staff page)
        Long activeBranchId = manager != null ? manager.getBranchId() : null;
        List<PublicMenu> items = activeBranchId != null
                ? publicMenuRepo.findByBranchIdOrderByCategoryAscNameAsc(activeBranchId)
                : List.of();
        model.addAttribute("menuItems", items);
        model.addAttribute("newItem", new PublicMenu());
        model.addAttribute("menuItemCount", items.size());
        model.addAttribute("activeBranchId", activeBranchId);

        if (manager != null && manager.isOrganizationLevel()
                && activeBranchId != null
                && managerAuthService.getBranchesForOrganization(manager.getOrganizationId()).size() > 1) {
            model.addAttribute("canImportMenu", true);
            model.addAttribute("importMenuUrl", "/manage/branch/" + activeBranchId + "/menu-import");
        } else {
            model.addAttribute("canImportMenu", false);
        }

        return "manage";
    }

    /**
     * Hand-rolled JSON serializer for the dashboard payload. We do this
     * by hand (no Jackson) so we don't pull in a new dependency, and so
     * the output is guaranteed valid JSON no matter what shape the
     * underlying Java collections take (Set, List, Map, etc).
     */
    private String buildDashboardJson(OrderService.DashboardStats stats) {
        try {
            StringBuilder sb = new StringBuilder(256);
            sb.append('{');
            sb.append("\"chartLabels\":").append(toJsonStringArray(stats.revenueByDate().keySet())).append(',');
            sb.append("\"chartData\":").append(toJsonNumberArray(stats.revenueByDate().values())).append(',');
            sb.append("\"bestCategoryLabels\":").append(toJsonStringArray(stats.bestSellingCategories().keySet())).append(',');
            sb.append("\"bestCategoryData\":").append(toJsonNumberArray(stats.bestSellingCategories().values())).append(',');
            sb.append("\"peakHourLabels\":").append(toJsonStringArray(stats.peakHours().keySet())).append(',');
            sb.append("\"peakHourData\":").append(toJsonNumberArray(stats.peakHours().values()));
            sb.append('}');
            return sb.toString();
        } catch (Exception e) {
            return "{\"chartLabels\":[],\"chartData\":[],\"bestCategoryLabels\":[],\"bestCategoryData\":[],\"peakHourLabels\":[],\"peakHourData\":[]}";
        }
    }

    /** Renders any Java collection of Strings as a JSON array of strings. */
    private String toJsonStringArray(java.util.Collection<?> coll) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('[');
        boolean first = true;
        for (Object o : coll) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escapeJson(String.valueOf(o))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    /** Renders any Java collection of Numbers as a JSON array of numbers. */
    private String toJsonNumberArray(java.util.Collection<? extends Number> coll) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('[');
        boolean first = true;
        for (Number n : coll) {
            if (!first) sb.append(',');
            first = false;
            sb.append(n == null ? "0" : String.valueOf(n.doubleValue()));
        }
        sb.append(']');
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }
    // --- NEW: STAFF ROUTE FOR STATUS UPDATES ---
    @GetMapping("/staff/status/{id}")
    public String updateItemStatus(@PathVariable Long id, @RequestParam String status,
                                   jakarta.servlet.http.HttpServletRequest request) {
        publicMenuRepo.findById(id).ifPresent(item -> {
            item.setStatus(status);
            publicMenuRepo.save(item);
        });
        return redirectBack(request);
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
                    out.put("items", orderItemRepo.findByOrderId(order.getId()).stream().map(this::toItemMap).toList());
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
        if (branch == null) {
            return new TableContext(null, null, "Invalid branch.");
        }
        // Allow table numbers beyond tableCount when setup is not yet
        // complete (tableCount may still be 0).  Once setup is done the
        // guard enforces the configured maximum.
        if (branch.isSetupComplete() && (tableNumber < 1 || tableNumber > branch.getTableCount())) {
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
