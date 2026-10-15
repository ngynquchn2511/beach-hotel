-- Phieu bao hong / bao tri phong (Moi -> Dang sua -> Xong), co the khoa phong (trang thai Bao tri).
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS maintenance_tickets (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    room_id             BIGINT       NOT NULL,
    title               VARCHAR(150) NOT NULL,
    description         VARCHAR(500) NULL,
    priority            VARCHAR(10)  NOT NULL,
    status              VARCHAR(12)  NOT NULL,
    blocks_room         BIT(1)       NOT NULL,
    expected_done_date  DATE         NULL,
    reported_by         VARCHAR(100) NULL,
    assignee            VARCHAR(100) NULL,
    resolution_note     VARCHAR(300) NULL,
    created_at          DATETIME(6)  NOT NULL,
    resolved_at         DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_mt_status (status, created_at),
    KEY idx_mt_room (room_id),
    CONSTRAINT fk_mt_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
