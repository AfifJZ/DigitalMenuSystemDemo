package com.example.menumanager.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    /** Maximum number of branches (premises) allowed for this organization. */
    @Column(nullable = false)
    private int branchLimit;

    /** True after the owner verifies their email with an OTP during registration. */
    @Column(nullable = false)
    private boolean emailVerified = false;

    /** Bank name for receiving manual transfers (Pay at Counter / DuitNow). */
    @Column(length = 100)
    private String payoutBankName;

    /** Bank account number for receiving manual transfers. */
    @Column(length = 50)
    private String payoutAccountNumber;

    /** Stripe Connect account for card/FPX payouts (auto-created when bank is saved). */
    @Column(length = 255)
    private String stripeAccountId;

    @Column(length = 255)
    private String stripeBankAccountId;

    public Organization() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public int getBranchLimit() { return branchLimit; }
    public void setBranchLimit(int branchLimit) { this.branchLimit = branchLimit; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public String getPayoutBankName() { return payoutBankName; }
    public void setPayoutBankName(String payoutBankName) { this.payoutBankName = payoutBankName; }
    public String getPayoutAccountNumber() { return payoutAccountNumber; }
    public void setPayoutAccountNumber(String payoutAccountNumber) { this.payoutAccountNumber = payoutAccountNumber; }
    public String getStripeAccountId() { return stripeAccountId; }
    public void setStripeAccountId(String stripeAccountId) { this.stripeAccountId = stripeAccountId; }
    public String getStripeBankAccountId() { return stripeBankAccountId; }
    public void setStripeBankAccountId(String stripeBankAccountId) { this.stripeBankAccountId = stripeBankAccountId; }
}
