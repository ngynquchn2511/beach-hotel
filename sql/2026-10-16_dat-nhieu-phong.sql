-- Dat nhieu phong 1 lan: nhom don (booking_groups) va cot bookings.group_id (NULL = don le).
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS booking_groups (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    customer_id   BIGINT       NOT NULL,
    guest_name    VARCHAR(100) NOT NULL,
    guest_email   VARCHAR(100) NULL,
    guest_phone   VARCHAR(20)  NULL,
    access_token  VARCHAR(32)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_groups_token (access_token),
    KEY idx_booking_groups_customer (customer_id),
    CONSTRAINT fk_booking_groups_customer FOREIGN KEY (customer_id) REFERENCES users (id)
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

CALL hb_add_column('bookings', 'group_id', 'BIGINT NULL');

DROP PROCEDURE hb_add_column;
