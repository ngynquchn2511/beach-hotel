-- Song ngu noi dung admin nhap (nut VI/EN o trang khach): them cot ban tieng Anh, bo trong = dung ban tieng Viet.
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

CALL hb_add_column('room_types', 'name_en', 'VARCHAR(100) NULL');
CALL hb_add_column('room_types', 'description_en', 'VARCHAR(1000) NULL');
CALL hb_add_column('room_types', 'amenities_en', 'VARCHAR(500) NULL');
CALL hb_add_column('rooms', 'description_en', 'VARCHAR(1000) NULL');
CALL hb_add_column('combos', 'name_en', 'VARCHAR(150) NULL');
CALL hb_add_column('combos', 'description_en', 'VARCHAR(1000) NULL');
CALL hb_add_column('discount_codes', 'description_en', 'VARCHAR(500) NULL');
CALL hb_add_column('special_rates', 'name_en', 'VARCHAR(100) NULL');

DROP PROCEDURE hb_add_column;
