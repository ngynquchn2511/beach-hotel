-- Thoi luong spa chot vao tung lich dat (giong gia): admin sua thoi luong goi khong lam doi lich da dat.
-- May dev (ddl-auto=update) Hibernate tu them cot khi khoi dong, nhung VAN PHAI chay file nay de dien cho lich cu.
-- Moi truong chay that (DDL_AUTO=validate) chay file nay TRUOC khi deploy ban moi. Chay lai nhieu lan van an toan.
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

CALL hb_add_column('facility_reservations', 'duration_minutes', 'INT NULL');

DROP PROCEDURE hb_add_column;

-- Lich spa cu: lay thoi luong hien tai cua goi
UPDATE facility_reservations r
JOIN spa_packages p ON p.id = r.spa_package_id
SET r.duration_minutes = p.minutes
WHERE r.kind = 'SPA' AND r.duration_minutes IS NULL;
