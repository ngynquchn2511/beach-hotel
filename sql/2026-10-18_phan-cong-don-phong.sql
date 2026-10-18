-- Phan cong don phong + buoc kiem phong: nguoi duoc giao, thoi diem bao don xong (cho kiem), ly do kiem chua dat;
-- cai dat bat / tat kiem phong.
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

CALL hb_add_column('rooms', 'cleaning_assignee_id', 'BIGINT NULL');
CALL hb_add_column('rooms', 'inspection_requested_at', 'DATETIME(6) NULL');
CALL hb_add_column('rooms', 'inspection_note', 'VARCHAR(300) NULL');
CALL hb_add_column('pricing_settings', 'housekeeping_inspection', 'BIT NULL');

DROP PROCEDURE hb_add_column;

-- Khoa ngoai nguoi duoc giao (chi tao khi chua co)
SET @fk := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rooms' AND CONSTRAINT_NAME = 'fk_room_cleaning_assignee');
SET @s := IF(@fk = 0, 'ALTER TABLE rooms ADD CONSTRAINT fk_room_cleaning_assignee FOREIGN KEY (cleaning_assignee_id) REFERENCES users (id)', 'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
