-- Chinh sach huy & hoan coc: moc gio hoan 100% / hoan mot phan, va ghi nhan tien hoan tren tung don huy.
-- May dev (ddl-auto=update) Hibernate tu them cot khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc.
-- Cot cai dat de NULL = dung mac dinh trong code (hoan 100% neu huy truoc 72h, 50% neu truoc 24h).
-- MySQL 8.0 khong ho tro "ADD COLUMN IF NOT EXISTS" -> dung thu tuc tam kiem tra information_schema,
-- cot nao co roi thi bo qua, chay lai nhieu lan van an toan.
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

CALL hb_add_column('pricing_settings', 'refund_full_hours', 'INT NULL');
CALL hb_add_column('pricing_settings', 'refund_partial_hours', 'INT NULL');
CALL hb_add_column('pricing_settings', 'refund_partial_percent', 'INT NULL');
CALL hb_add_column('bookings', 'cancelled_at', 'DATETIME(6) NULL');
CALL hb_add_column('bookings', 'refund_percent', 'INT NULL');
CALL hb_add_column('bookings', 'refund_amount', 'DECIMAL(12,2) NULL');
CALL hb_add_column('bookings', 'refunded_at', 'DATETIME(6) NULL');
CALL hb_add_column('bookings', 'refund_note', 'VARCHAR(255) NULL');

DROP PROCEDURE hb_add_column;
