-- Xac thuc email khi dang ky: tai khoan tu dang ky / tu tao khi dat phong phai bam link gui toi email moi dang nhap duoc.
-- email_verified NULL = tai khoan cu / admin / nhan vien -> coi nhu da xac thuc (khong khoa ai dang dung).
-- May dev (ddl-auto=update) Hibernate tu them cot khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc.
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

CALL hb_add_column('users', 'email_verified', 'BIT(1) NULL');
CALL hb_add_column('users', 'email_verify_token', 'VARCHAR(64) NULL');
CALL hb_add_column('users', 'email_verify_expiry', 'DATETIME(6) NULL');

DROP PROCEDURE hb_add_column;
