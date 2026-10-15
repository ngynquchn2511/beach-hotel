-- "Bao toi khi phong trong lai": khach de lai email cho khoang ngay dang kin, phong trong lai thi gui email 1 lan.
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
-- ON DELETE CASCADE: xoa phong thi tu xoa cac yeu cau lien quan.
CREATE TABLE IF NOT EXISTS room_alerts (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    room_id      BIGINT       NOT NULL,
    email        VARCHAR(100) NOT NULL,
    check_in     DATE         NOT NULL,
    check_out    DATE         NOT NULL,
    guests       INT          NULL,
    language     VARCHAR(5)   NULL,
    created_at   DATETIME(6)  NOT NULL,
    notified_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_room_alert_pending (notified_at, check_in),
    KEY idx_room_alerts_room (room_id),
    CONSTRAINT fk_room_alerts_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
