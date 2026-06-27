package com.example.menumanager.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.menumanager.model.Organization;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;

/**
 * Stripe Connect helper for the MY-platform case.
 *
 * <p>Stripe does NOT allow MY-registered platforms to use Connect in
 * "platform loss-liable" mode (destination charges), so we use
 * {@link AccountCreateParams.Type#EXPRESS} instead. With Express:
 *
 * <ul>
 *   <li>Stripe hosts the bank / KYC collection form for the connected
 *       restaurant (the "organization" in our domain).</li>
 *   <li>The restaurant is loss-liable for its own chargebacks, which is
 *       the model Stripe allows in Malaysia.</li>
 *   <li>Our app only needs to (1) create the Express account (if it
 *       doesn't exist), (2) generate a one-time onboarding link with
 *       {@code AccountLink.create(...)} and redirect the user there.</li>
 * </ul>
 *
 * <p>The previously-stored {@code payoutBankName} / {@code payoutAccountNumber}
 * fields in {@link Organization} are kept as a local display copy (shown
 * to customers on the "Pay at Counter" instructions). The actual bank
 * account is stored and managed by Stripe via the hosted onboarding.
 *
 * <p>Set {@code app.stripe.connect.enabled=false} in
 * {@code application.properties} to skip the Stripe call entirely (for
 * local demos without a Stripe key).
 */
@Service
public class StripeConnectService {

    @Value("${stripe.secretKey:}")
    private String stripeSecretKey;

    @Value("${app.stripe.connect.enabled:true}")
    private boolean connectEnabled;

    @Value("${app.publicBaseUrl:http://localhost:8080}")
    private String publicBaseUrl;

    public boolean isConnectEnabled() {
        return connectEnabled;
    }

    /**
     * Creates (or refreshes) the Express Connect account for this
     * organization. Returns the account id, or an error message if Stripe
     * rejected the request.
     *
     * <p>This no longer pushes the bank account number to Stripe directly
     * (that was the old "loss-liable" path). The bank is collected by
     * Stripe later via the hosted onboarding link.
     */
    public Optional<String> createOrUpdateExpressAccount(Organization org) {
        if (!connectEnabled) {
            return Optional.of("Stripe Connect is disabled in application.properties.");
        }
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            return Optional.of("Stripe is not configured. Contact the platform administrator.");
        }

        Stripe.apiKey = stripeSecretKey;
        try {
            String accountId = org.getStripeAccountId();
            if (accountId != null && !accountId.isBlank()) {
                return Optional.of(accountId);
            }
            Account account = Account.create(AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setCountry("MY")
                    .setEmail(org.getEmail())
                    .setBusinessType(AccountCreateParams.BusinessType.INDIVIDUAL)
                    .setBusinessProfile(AccountCreateParams.BusinessProfile.builder()
                            .setName(org.getName())
                            .build())
                    .setCapabilities(AccountCreateParams.Capabilities.builder()
                            .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder()
                                    .setRequested(true)
                                    .build())
                            .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                                    .setRequested(true)
                                    .build())
                            .build())
                    .putMetadata("organizationId", String.valueOf(org.getId()))
                    .build());
            org.setStripeAccountId(account.getId());
            return Optional.of(account.getId());
        } catch (StripeException e) {
            return Optional.of("Could not create Stripe Connect account: "
                    + (e.getUserMessage() != null ? e.getUserMessage() : e.getMessage()));
        }
    }

    /**
     * Generates a one-time hosted onboarding link for the connected account.
     * The user must visit this URL in their browser to enter the bank and
     * KYC details that Stripe will hold.
     *
     * @return the onboarding URL, or an error message if it could not be
     *         generated.
     */
    public Optional<String> createOnboardingLink(Organization org) {
        if (!connectEnabled) {
            return Optional.empty();
        }
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            return Optional.empty();
        }

        // Make sure the account exists first.
        var accIdOrError = createOrUpdateExpressAccount(org);
        if (accIdOrError.isEmpty()) {
            return Optional.empty();
        }
        String accountId = accIdOrError.get();
        
        // Validate we got a real Stripe account ID, not an error message
        if (!accountId.startsWith("acct_")) {
            // This is an error message, not an account ID
            return Optional.empty();
        }

        Stripe.apiKey = stripeSecretKey;
        try {
            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                    .setAccount(accountId)
                    .setRefreshUrl(publicBaseUrl + "/manage/profile?onboarding=refresh")
                    .setReturnUrl(publicBaseUrl + "/manage/profile?onboarding=return")
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build());
            return Optional.of(link.getUrl());
        } catch (StripeException e) {
            return Optional.empty();
        }
    }

    /**
     * Checks whether the connected account has finished onboarding and is
     * ready to receive payouts (i.e. the user completed the hosted form
     * and Stripe verified the bank).
     */
    public boolean isAccountReadyToReceivePayouts(Organization org) {
        if (!connectEnabled || stripeSecretKey == null || stripeSecretKey.isBlank()) {
            return false;
        }
        String accountId = org.getStripeAccountId();
        if (accountId == null || accountId.isBlank()) return false;

        Stripe.apiKey = stripeSecretKey;
        try {
            Account account = Account.retrieve(accountId);
            // For Express accounts, payouts_enabled flips to true once
            // Stripe has the verified bank + KYC.
            return Boolean.TRUE.equals(account.getPayoutsEnabled())
                    && account.getRequirements() != null
                    && (account.getRequirements().getCurrentlyDue() == null
                        || account.getRequirements().getCurrentlyDue().isEmpty());
        } catch (StripeException e) {
            return false;
        }
    }
}
