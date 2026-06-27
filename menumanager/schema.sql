-- =============================================================
-- Digital Menu System — MySQL Database Schema
-- Run this script to create the database and all tables.
-- Alternatively, Hibernate ddl-auto=update auto-creates them.
-- =============================================================

CREATE DATABASE IF NOT EXISTS digital_menu_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE digital_menu_db;

-- =============================================================
-- 1. organizations — Accounts for restaurant groups / owners
-- =============================================================
CREATE TABLE IF NOT EXISTS organizations (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    name                VARCHAR(255)    NOT NULL,
    email               VARCHAR(255)    NOT NULL,
    password_hash       VARCHAR(255)    NOT NULL,
    branch_limit        INT             NOT NULL DEFAULT 1,
    email_verified      BOOLEAN         NOT NULL DEFAULT FALSE,
    payout_bank_name    VARCHAR(100)    DEFAULT NULL,
    payout_account_number VARCHAR(50)   DEFAULT NULL,
    stripe_account_id   VARCHAR(255)    DEFAULT NULL,
    stripe_bank_account_id VARCHAR(255)  DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_organizations_name (name),
    UNIQUE KEY uk_organizations_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- 2. branches — Individual premises / locations
-- =============================================================
CREATE TABLE IF NOT EXISTS branches (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    organization_id   BIGINT          NOT NULL,
    name              VARCHAR(255)    NOT NULL,
    location          VARCHAR(255)    DEFAULT NULL,
    table_count       INT             NOT NULL DEFAULT 0,
    password_hash     VARCHAR(255)    NOT NULL,
    setup_complete    BOOLEAN         NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_branches_organization (organization_id),
    CONSTRAINT fk_branches_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- 3. menu_items — Public menu items visible to customers
-- =============================================================
CREATE TABLE IF NOT EXISTS menu_items (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    name          VARCHAR(255)    DEFAULT NULL,
    description   TEXT            DEFAULT NULL,
    price         DOUBLE          DEFAULT NULL,
    category      VARCHAR(255)    DEFAULT NULL,
    image_url     VARCHAR(255)    DEFAULT NULL,
    status        VARCHAR(255)    NOT NULL DEFAULT 'AVAILABLE',
    branch_id     BIGINT          DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_menu_items_branch (branch_id),
    KEY idx_menu_items_category (category),
    CONSTRAINT fk_menu_items_branch
        FOREIGN KEY (branch_id) REFERENCES branches (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- 4. menu_item — Internal (staff) menu item catalog
-- =============================================================
CREATE TABLE IF NOT EXISTS menu_item (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    name          VARCHAR(255)    DEFAULT NULL,
    description   TEXT            DEFAULT NULL,
    price         DOUBLE          DEFAULT NULL,
    category      VARCHAR(255)    DEFAULT NULL,
    image_url     VARCHAR(255)    DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- 5. orders — Customer orders with Stripe payment tracking
-- =============================================================
CREATE TABLE IF NOT EXISTS orders (
    id                        BIGINT          NOT NULL AUTO_INCREMENT,
    order_time                DATETIME(6)     DEFAULT NULL,
    total_amount              DOUBLE          DEFAULT NULL,
    order_number              VARCHAR(255)    DEFAULT NULL,
    branch_id                 BIGINT          DEFAULT NULL,
    table_number              INT             DEFAULT NULL,
    status                    VARCHAR(255)    NOT NULL DEFAULT 'KITCHEN',
    staff_note                TEXT            DEFAULT NULL,
    stripe_session_id         VARCHAR(255)    DEFAULT NULL,
    stripe_payment_intent_id  VARCHAR(255)    DEFAULT NULL,
    paid_at                   DATETIME(6)     DEFAULT NULL,
    refunded_at               DATETIME(6)     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_orders_branch (branch_id),
    KEY idx_orders_status (status),
    KEY idx_orders_order_time (order_time),
    CONSTRAINT fk_orders_branch
        FOREIGN KEY (branch_id) REFERENCES branches (id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- 6. order_item — Individual line items within an order
-- =============================================================
CREATE TABLE IF NOT EXISTS order_item (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    order_id      BIGINT          DEFAULT NULL,
    name          VARCHAR(255)    DEFAULT NULL,
    price         DOUBLE          DEFAULT NULL,
    quantity      INT             DEFAULT NULL,
    note          TEXT            DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id),
    CONSTRAINT fk_order_item_order
        FOREIGN KEY (order_id) REFERENCES orders (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
