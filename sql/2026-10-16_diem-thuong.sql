-- Diem thuong thanh vien: so du cua khach, diem dung / duoc cong tren tung don, lich su cong tru diem.
-- loyalty_points NULL = tai khoan cu, coi nhu 0 diem.
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
-- MySQL 8.0 khong ho tro "ADD COLUMN IF NOT EXISTS" -> dung thu tuc tam kiem tra information_schema
DELIMITER //
DROP PROCEDURE IF EXISTS hb_add_column //
CREATE PROCEDURE hb_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN definition VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL hb_add_column('users', 'loyalty_points', 'INT NULL');
CALL hb_add_column('bookings', 'points_used', 'INT NULL');
CALL hb_add_column('bookings', 'points_discount', 'DECIMAL(12,2) NULL');
CALL hb_add_column('bookings', 'points_earned', 'INT NULL');

DROP PROCEDURE hb_add_column;

-- Xoa tai khoan thi xoa lich su diem; xoa don thi giu lich su (booking_id ve NULL).
CREATE TABLE IF NOT EXISTS point_transactions (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    booking_id     BIGINT      NULL,
    type           VARCHAR(10) NOT NULL,
    points         INT         NOT NULL,
    balance_after  INT         NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_point_tx_user (user_id, created_at),
    KEY idx_point_tx_booking (booking_id),
    CONSTRAINT fk_point_tx_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_point_tx_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
