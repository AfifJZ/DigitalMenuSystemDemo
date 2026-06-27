package com.example.menumanager.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.menumanager.model.Order;
import com.example.menumanager.model.OrderItem;
import com.example.menumanager.model.Organization;
import com.example.menumanager.repository.BranchRepository;
import com.example.menumanager.repository.OrderRepository;
import com.example.menumanager.repository.OrganizationRepository;
import com.example.menumanager.service.OrderService;
import com.example.menumanager.service.StripeConnectService;
import com.stripe.net.RequestOptions;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;

@RestController
@RequestMapping("/api/payments/stripe")
public class PaymentController {

    @Autowired private OrderRepository orderRepo;
    @Autowired private OrderService orderService;
    @Autowired private BranchRepository branchRepo;
    @Autowired private OrganizationRepository organizationRepo;
    @Autowired private StripeConnectService stripeConnectService;

    @Value("${stripe.secretKey:}")
    private String stripeSecretKey;

    @Value("${stripe.webhookSecret:}")
    private String stripeWebhookSecret;

    @Value("${app.publicBaseUrl:http://localhost:8080}")
    private String publicBaseUrl;

    /** Stripe Checkout minimum for MYR (cards + FPX — enforced by Stripe, not removable). */
    @Value("${app.payment.stripe-min-amount-myr:2.00}")
    private double stripeMinAmountMyr;

    /**
     * Creates an order (ONLINE_PAYMENT_PENDING), then starts Stripe Checkout.
     * CARD → Stripe card only. ONLINE → Stripe FPX (Malaysian online banking).
     */
    @PostMapping("/checkout-session")
    public ResponseEntity<?> createCheckoutSession(
            @RequestBody List<Map<String, Object>> cartItems,
            @RequestParam String paymentMethod,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Integer tableNumber
    ) throws StripeException {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "ok", false,
                    "error", "Stripe is not configured. Set stripe.secretKey in application.properties"
            ));
        }

        try {
            Order newOrder = orderService.createOrderFromCart(cartItems, paymentMethod, branchId, tableNumber);
            double total = newOrder.getTotalAmount() != null ? newOrder.getTotalAmount() : 0.0;

            // 2) Create Stripe Checkout Session
            Stripe.apiKey = stripeSecretKey;

            long amountInSen = Math.round(total * 100.0); // RM -> sen
            if (amountInSen <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "ok", false,
                        "error", "Cart total must be greater than 0"
                ));
            }

            boolean isCard = "CARD".equalsIgnoreCase(paymentMethod);
            boolean isOnlineBanking = "ONLINE".equalsIgnoreCase(paymentMethod);
            if (!isCard && !isOnlineBanking) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "ok", false,
                        "error", "Unsupported online payment method."
                ));
            }

            long stripeMinSen = Math.round(stripeMinAmountMyr * 100.0);
            if (amountInSen < stripeMinSen) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "ok", false,
                        "error", String.format(
                                "Online payments (card & FPX) require a minimum of RM %.2f (Stripe rule). Use Pay at Counter for smaller amounts.",
                                stripeMinAmountMyr)
                ));
            }

            String successUrl = publicBaseUrl + "/api/payments/stripe/success?session_id={CHECKOUT_SESSION_ID}";
            String cancelUrl = publicBaseUrl + "/api/payments/stripe/cancel?orderId=" + newOrder.getId();

            SessionCreateParams.Builder sessionBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("myr")
                                    .setUnitAmount(amountInSen)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Order #" + newOrder.getId())
                                            .build())
                                    .build())
                            .build())
                    .putMetadata("orderId", String.valueOf(newOrder.getId()))
                    .putMetadata("paymentMethod", paymentMethod.toUpperCase());

            if (isCard) {
                sessionBuilder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD);
            } else {
                // FPX = Malaysian online banking (Maybank, CIMB, etc.) via Stripe
                sessionBuilder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.FPX);
            }

            SessionCreateParams params = sessionBuilder.build();

            String connectedAccountId = resolveStripeAccountId(branchId);
            if (stripeConnectService.isConnectEnabled()) {
                if (connectedAccountId == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                            "ok", false,
                            "error", "Online payment is not available. Please scan the table QR code or use Pay at Counter."
                    ));
                }
            }

            Session session;
            if (connectedAccountId != null) {
                RequestOptions requestOptions = RequestOptions.builder()
                        .setStripeAccount(connectedAccountId)
                        .build();
                session = Session.create(params, requestOptions);
            } else {
                session = Session.create(params);
            }

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "orderId", newOrder.getId(),
                    "checkoutUrl", session.getUrl()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "ok", false,
                    "error", e.getMessage()
            ));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "ok", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "ok", false,
                    "error", "Server error creating payment session"
            ));
        }
    }

    /**
     * Success redirect target after Stripe Checkout.
     * This verifies the session is PAID and then moves the order to KITCHEN.
     *
     * This makes local dev work even if webhooks aren't running.
     */
    @GetMapping("/success")
    public ResponseEntity<Void> stripeSuccess(@RequestParam(name = "session_id") String sessionId) throws StripeException {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/customer?payment=failed").build();
        }

        Stripe.apiKey = stripeSecretKey;
        Session session = Session.retrieve(sessionId);

        String orderIdStr = session.getMetadata() != null ? session.getMetadata().get("orderId") : null;
        boolean paid = "paid".equalsIgnoreCase(session.getPaymentStatus());

        if (paid && orderIdStr != null && !orderIdStr.isBlank()) {
            Long orderId = Long.valueOf(orderIdStr);
            orderRepo.findById(orderId).ifPresent(order -> {
                order.setStripeSessionId(sessionId);
                order.setStripePaymentIntentId(session.getPaymentIntent());
                order.setPaidAt(LocalDateTime.now());
                orderRepo.save(order);
            });
            orderService.updateOrderStatus(orderId, "KITCHEN");
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", "/customer?payment=success&orderId=" + orderIdStr)
                    .build();
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "/customer?payment=failed")
                .build();
    }

    @GetMapping("/cancel")
    public ResponseEntity<Void> stripeCancel(@RequestParam(name = "orderId") Long orderId) {
        // Leave the order as pending; you can add cleanup later if you want.
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "/customer?payment=cancelled&orderId=" + orderId)
                .build();
    }

    /**
     * Stripe webhook: when payment completes, automatically push order to KITCHEN.
     * For local dev, use Stripe CLI to forward webhooks to:
     *   http://localhost:8080/api/payments/stripe/webhook
     */
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String sigHeader
    ) {
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            // In production you should NOT accept unsigned webhooks.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook secret not configured");
        }

        try {
            Event event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);

            if ("checkout.session.completed".equals(event.getType())) {
                Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                if (session != null) {
                    String orderIdStr = session.getMetadata() != null ? session.getMetadata().get("orderId") : null;
                    if (orderIdStr != null && !orderIdStr.isBlank()) {
                        Long orderId = Long.valueOf(orderIdStr);
                        orderRepo.findById(orderId).ifPresent(order -> {
                            order.setStripeSessionId(session.getId());
                            order.setStripePaymentIntentId(session.getPaymentIntent());
                            if (order.getPaidAt() == null) order.setPaidAt(LocalDateTime.now());
                            orderRepo.save(order);
                        });
                        orderService.updateOrderStatus(orderId, "KITCHEN");
                    }
                }
            }

            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook error");
        }
    }

    private String resolveStripeAccountId(Long branchId) {
        if (branchId == null) {
            return null;
        }
        return branchRepo.findById(branchId)
                .map(branch -> organizationRepo.findById(branch.getOrganization().getId()))
                .flatMap(opt -> opt.map(Organization::getStripeAccountId))
                .filter(id -> id != null && !id.isBlank())
                .orElse(null);
    }
}

