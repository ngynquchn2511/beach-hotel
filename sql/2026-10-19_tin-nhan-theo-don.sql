-- Tin nhan giua khach va le tan theo don.
-- May dev (ddl-auto=update) Hibernate tu tao bang khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS booking_messages (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    booking_id  BIGINT        NOT NULL,
    from_guest  BIT           NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    staff_name  VARCHAR(100)  NULL,
    created_at  DATETIME(6)   NOT NULL,
    read_at     DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_booking_message (booking_id, created_at),
    CONSTRAINT fk_booking_message_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
