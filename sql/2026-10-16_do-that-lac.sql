-- So do that lac: do khach de quen (gan phong / don), dang cat -> da tra khach / da thanh ly.
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS lost_items (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    room_id            BIGINT       NULL,
    booking_id         BIGINT       NULL,
    description        VARCHAR(200) NOT NULL,
    storage_location   VARCHAR(100) NULL,
    found_by           VARCHAR(100) NULL,
    found_at           DATETIME(6)  NOT NULL,
    status             VARCHAR(10)  NOT NULL,
    guest_notified_at  DATETIME(6)  NULL,
    closed_at          DATETIME(6)  NULL,
    close_note         VARCHAR(200) NULL,
    PRIMARY KEY (id),
    KEY idx_lost_status (status, found_at),
    KEY idx_lost_room (room_id),
    KEY idx_lost_booking (booking_id),
    CONSTRAINT fk_lost_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE SET NULL,
    CONSTRAINT fk_lost_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
