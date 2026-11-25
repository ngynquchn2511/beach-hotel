-- Buong phong tu phan viec: luc giao phong don, luc giao / nhan kiem phong truoc khi tra, so lan he thong tu chuyen nguoi kiem.
-- Moi truong chay that (DDL_AUTO=validate) phai chay TRUOC khi deploy ban moi. Chay lai nhieu lan van an toan.
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

CALL hb_add_column('rooms', 'cleaning_assigned_at', 'DATETIME(6) NULL');
CALL hb_add_column('bookings', 'room_check_assigned_at', 'DATETIME(6) NULL');
CALL hb_add_column('bookings', 'room_check_accepted_at', 'DATETIME(6) NULL');
CALL hb_add_column('bookings', 'room_check_escalations', 'INT NULL');
DROP PROCEDURE hb_add_column;
