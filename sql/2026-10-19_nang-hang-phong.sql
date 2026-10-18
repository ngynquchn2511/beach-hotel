-- Ban nang hang phong: loi moi theo don (loai phong / phong cu the, gia, trang thai) + cai dat uu dai / tu gui.
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS upgrade_offers (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    booking_id       BIGINT        NOT NULL,
    room_id          BIGINT        NOT NULL,
    full_difference  DECIMAL(12,2) NOT NULL,
    price            DECIMAL(12,2) NOT NULL,
    status           VARCHAR(12)   NOT NULL,
    expires_at       DATETIME(6)   NOT NULL,
    created_by       VARCHAR(100)  NULL,
    created_at       DATETIME(6)   NOT NULL,
    responded_at     DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_upgrade_booking (booking_id, status),
    KEY idx_upgrade_room (room_id),
    CONSTRAINT fk_upgrade_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_upgrade_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

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

CALL hb_add_column('pricing_settings', 'upgrade_discount_percent', 'INT NULL');
CALL hb_add_column('pricing_settings', 'upgrade_auto_offer', 'BIT NULL');

DROP PROCEDURE hb_add_column;
