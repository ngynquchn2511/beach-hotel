-- Lich su don phong / kiem phong: moi luot don 1 dong (nguoi don, thoi gian, ket qua kiem) de thong ke buong phong.
-- May dev (ddl-auto=update) Hibernate tu tao bang khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS housekeeping_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    room_id       BIGINT       NOT NULL,
    cleaner_id    BIGINT       NULL,
    cleaner_name  VARCHAR(100) NULL,
    started_at    DATETIME(6)  NULL,
    finished_at   DATETIME(6)  NOT NULL,
    result        VARCHAR(10)  NOT NULL,
    inspected_by  VARCHAR(100) NULL,
    note          VARCHAR(300) NULL,
    PRIMARY KEY (id),
    KEY idx_hk_log_time (finished_at),
    KEY idx_hk_log_room (room_id),
    KEY idx_hk_log_cleaner (cleaner_id),
    CONSTRAINT fk_hk_log_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_hk_log_cleaner FOREIGN KEY (cleaner_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
