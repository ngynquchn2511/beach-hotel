-- Hoa don cong ty (VAT): thong tin don vi tren tung don + so hoa don dien tu ke toan da xuat.
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

CALL hb_add_column('bookings', 'invoice_company_name', 'VARCHAR(200) NULL');
CALL hb_add_column('bookings', 'invoice_tax_code', 'VARCHAR(20) NULL');
CALL hb_add_column('bookings', 'invoice_address', 'VARCHAR(255) NULL');
CALL hb_add_column('bookings', 'invoice_email', 'VARCHAR(100) NULL');
CALL hb_add_column('bookings', 'e_invoice_number', 'VARCHAR(30) NULL');
CALL hb_add_column('bookings', 'e_invoice_issued_at', 'DATETIME(6) NULL');

DROP PROCEDURE hb_add_column;
