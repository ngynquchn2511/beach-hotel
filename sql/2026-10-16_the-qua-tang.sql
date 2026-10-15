-- The qua tang: the ban online + ma / so tien the da tru tren tung don.
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS gift_vouchers (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    code             VARCHAR(20)   NOT NULL,
    amount           DECIMAL(12,2) NOT NULL,
    balance          DECIMAL(12,2) NOT NULL,
    status           VARCHAR(16)   NOT NULL,
    purchaser_name   VARCHAR(100)  NOT NULL,
    purchaser_email  VARCHAR(100)  NOT NULL,
    recipient_name   VARCHAR(100)  NOT NULL,
    recipient_email  VARCHAR(100)  NOT NULL,
    message          VARCHAR(300)  NULL,
    online_ref       VARCHAR(40)   NULL,
    paid_at          DATETIME(6)   NULL,
    expires_at       DATETIME(6)   NULL,
    access_token     VARCHAR(32)   NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gift_vouchers_code (code),
    UNIQUE KEY uk_gift_vouchers_token (access_token)
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

CALL hb_add_column('bookings', 'gift_voucher_code', 'VARCHAR(20) NULL');
CALL hb_add_column('bookings', 'gift_voucher_amount', 'DECIMAL(12,2) NULL');

DROP PROCEDURE hb_add_column;
