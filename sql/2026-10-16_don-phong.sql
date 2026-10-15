-- Bang don phong cua le tan: luc phong bat dau can don, ai dang don / bat dau luc nao, lan don xong gan nhat.
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

CALL hb_add_column('rooms', 'dirty_since', 'DATETIME(6) NULL');
CALL hb_add_column('rooms', 'cleaning_by', 'VARCHAR(100) NULL');
CALL hb_add_column('rooms', 'cleaning_started_at', 'DATETIME(6) NULL');
CALL hb_add_column('rooms', 'last_cleaned_at', 'DATETIME(6) NULL');
CALL hb_add_column('rooms', 'last_cleaned_by', 'VARCHAR(100) NULL');

DROP PROCEDURE hb_add_column;
