-- Gioi han ma giam gia: thoi han, tong so luot, gia tri don toi thieu, muc giam toi da (ma %).
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

CALL hb_add_column('discount_codes', 'valid_from', 'DATE NULL');
CALL hb_add_column('discount_codes', 'valid_to', 'DATE NULL');
CALL hb_add_column('discount_codes', 'max_uses', 'INT NULL');
CALL hb_add_column('discount_codes', 'min_order_amount', 'DECIMAL(12,2) NULL');
CALL hb_add_column('discount_codes', 'max_discount_amount', 'DECIMAL(12,2) NULL');

DROP PROCEDURE hb_add_column;
