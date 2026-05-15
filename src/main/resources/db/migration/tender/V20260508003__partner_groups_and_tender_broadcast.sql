-- =============================================================================
-- Migration:  V20260508003__partner_groups_and_tender_broadcast.sql
-- Module:     tender
-- Date:       2026-05-08
-- PR:         R1 (front-end-driven gap closure)
--
-- WHAT THIS MIGRATION DOES:
--   Lands the tender-broadcast / partner-network surface the front-end's
--   "My Network" tab and Tender broadcast/award lifecycle expect:
--
--     1. partner_groups               — tenant-scoped partner groupings
--     2. partner_group_members        — partner ↔ group junction
--     3. tenders ALTERs               — goods_type, sent_via, broadcast_partner_count, awarded_bid_id
--     4. tender_broadcast_groups      — which groups a tender was sent to (drives partner-portal visibility)
--     5. tender_assignments           — vehicle + driver submitted by winning partner (1:1 with tenders)
--
-- DEPENDENCIES:
--   - partner_tp_profiles must exist. v5.0 schema declares it (lines 172–191) but
--     this project does not yet have a Flyway migration that creates it. Future
--     PR R5 will land partner_tp_profiles. Until then the FK constraints below
--     will fail on a fresh DB — the CREATE TABLE for partner_tp_profiles is
--     included inline here as a temporary measure so the migration is
--     self-contained and PR R1 can be smoke-tested. R5 will rewrite this section
--     into its own migration and remove the inline CREATE TABLE.
--
-- SOURCE:
--   Sections 2–6 of FinalVersion/database/V20260507006__partner_groups_and_tender_broadcast.sql.
-- =============================================================================


-- ── 0. partner_tp_profiles (TEMPORARY — will move to PR R5's user-module migration) ──
-- Must exist before partner_group_members.partner_id and tenders.awarded_to FKs can resolve.
-- Schema reference: schema.sql v5.0 lines 172–191.
CREATE TABLE IF NOT EXISTS partner_tp_profiles (

    id                CHAR(36)        NOT NULL,
    user_id           CHAR(36)        NOT NULL,
    company_name      VARCHAR(255)    NOT NULL,
    service_zone      VARCHAR(20)     NULL     COMMENT 'NORTH|SOUTH|EAST|WEST|OWN_NETWORK',
    gstin             VARCHAR(15)     NULL,
    no_gst            BOOLEAN         NOT NULL DEFAULT FALSE,
    vehicles          JSON            NULL     COMMENT 'Array of vehicle type strings',
    languages         JSON            NULL     COMMENT 'Array of language codes',
    address_line_1    VARCHAR(255)    NULL,
    city              VARCHAR(100)    NULL,
    state             VARCHAR(100)    NULL,
    pincode           VARCHAR(6)      NULL,
    is_active         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ptp_user (user_id),
    CONSTRAINT fk_ptp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Logistics Partner profile. Receives tender invitations.';


-- ── 1. partner_groups ────────────────────────────────────────────────────────
-- Tenant-scoped grouping of Logistics Partners. Created by a TP to target
-- tender broadcasts ("North India Express", "South Refrigerated", etc.).
CREATE TABLE partner_groups (

    id            CHAR(36)        NOT NULL,
    tp_account_id CHAR(36)        NOT NULL COMMENT 'Tenant scope. Group is private to the TP that created it.',
    name          VARCHAR(150)    NOT NULL,
    description   VARCHAR(500)    NULL,
    is_active     BOOLEAN         NOT NULL DEFAULT TRUE
                  COMMENT 'Soft-delete. FALSE = hidden from tender broadcast targeting.',
    created_by    CHAR(36)        NOT NULL COMMENT 'FK users.id of TP_ADMIN who created the group.',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pg_tp_name (tp_account_id, name),
    INDEX idx_pg_tp     (tp_account_id),
    INDEX idx_pg_active (tp_account_id, is_active),
    CONSTRAINT fk_pg_tp      FOREIGN KEY (tp_account_id) REFERENCES tp_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pg_creator FOREIGN KEY (created_by)    REFERENCES users(id)       ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Partner groups defined by a TP for tender broadcast targeting.';


-- ── 2. partner_group_members ─────────────────────────────────────────────────
-- Many-to-many junction. Group delete cascades; partner stays.
CREATE TABLE partner_group_members (

    group_id   CHAR(36)        NOT NULL,
    partner_id CHAR(36)        NOT NULL COMMENT 'FK partner_tp_profiles.id',
    added_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, partner_id),
    INDEX idx_pgm_partner (partner_id),
    CONSTRAINT fk_pgm_group   FOREIGN KEY (group_id)   REFERENCES partner_groups(id)      ON DELETE CASCADE,
    CONSTRAINT fk_pgm_partner FOREIGN KEY (partner_id) REFERENCES partner_tp_profiles(id) ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Partner ↔ group junction. Group delete cascades; partner stays.';


-- ── 3. tenders — broadcast + award columns ───────────────────────────────────
-- Front-end's Tender DTO carries goodsType, sentVia, broadcastPartnerCount, awardedBidId.
ALTER TABLE tenders
    ADD COLUMN goods_type VARCHAR(100) NULL
        COMMENT 'Goods category — front-end Tender DTO carries it.'
        AFTER vehicle_type,
    ADD COLUMN sent_via JSON NULL
        COMMENT 'Channels broadcast was sent through. Subset of ["app","whatsapp"]. Appended on each broadcast write.',
    ADD COLUMN broadcast_partner_count INT NOT NULL DEFAULT 0
        COMMENT 'Cached count of distinct partners reached. Recomputed on each broadcast write.',
    ADD COLUMN awarded_bid_id CHAR(36) NULL
        COMMENT 'FK bids.id — the winning bid. tenders.awarded_to (partner) is derivable from this row.';

ALTER TABLE tenders
    ADD CONSTRAINT fk_tender_awarded_bid
        FOREIGN KEY (awarded_bid_id) REFERENCES bids(id) ON DELETE SET NULL;


-- ── 4. tender_broadcast_groups ───────────────────────────────────────────────
-- Junction: which partner_groups a tender was broadcast to. Partner-portal visibility:
-- a partner sees a tender iff one of their groups is in this junction.
CREATE TABLE tender_broadcast_groups (

    tender_id    CHAR(36)        NOT NULL,
    group_id     CHAR(36)        NOT NULL,
    broadcast_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tender_id, group_id),
    INDEX idx_tbg_group (group_id),
    CONSTRAINT fk_tbg_tender FOREIGN KEY (tender_id) REFERENCES tenders(id)        ON DELETE CASCADE,
    CONSTRAINT fk_tbg_group  FOREIGN KEY (group_id)  REFERENCES partner_groups(id) ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Junction: which partner_groups a tender was broadcast to. Drives partner-portal visibility.';


-- ── 5. tender_assignments ────────────────────────────────────────────────────
-- 1:1 with tenders. Set when the awarded partner submits vehicle + driver via
-- POST /partner/tenders/:id/assign. Locks once written — UI hides the submit form.
CREATE TABLE tender_assignments (

    tender_id           CHAR(36)        NOT NULL,
    vehicle_number      VARCHAR(20)     NOT NULL COMMENT 'UPPERCASE. Required.',
    driver_name         VARCHAR(255)    NOT NULL,
    driver_dl           VARCHAR(30)     NULL     COMMENT 'UPPERCASE. Optional. Sarathi advisory only.',
    assigned_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by_user_id CHAR(36)        NOT NULL COMMENT 'FK users.id — the PARTNER_TP user who submitted the assignment.',
    PRIMARY KEY (tender_id),
    -- Constraint / index prefix is `tasn_` (tender_assignments) to avoid
    -- collision with tp_assignments which already owns the `ta_` namespace at
    -- the DB level. MySQL requires FK constraint names to be unique across the
    -- whole database; an earlier draft used `fk_ta_tender` and clashed at apply
    -- time (Error 1826: Duplicate foreign key constraint name 'fk_ta_tender').
    INDEX idx_tasn_user (assigned_by_user_id),
    CONSTRAINT fk_tasn_tender FOREIGN KEY (tender_id)           REFERENCES tenders(id) ON DELETE CASCADE,
    CONSTRAINT fk_tasn_user   FOREIGN KEY (assigned_by_user_id) REFERENCES users(id)   ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Vehicle + driver assigned to a tender by the winning partner. 1:1 with tenders. Locks once written.';
