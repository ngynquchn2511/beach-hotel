-- Kiem phong truoc khi tra phong: le tan yeu cau, nhan vien buong phong kiem do mat / hu hong roi bao lai.
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi. Chay lai nhieu lan van an toan.
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

CALL hb_add_column('bookings', 'room_check_requested_at', 'DATETIME(6) NULL');
CALL hb_add_column('bookings', 'room_check_requested_by', 'VARCHAR(100) NULL');
CALL hb_add_column('bookings', 'room_checked_at', 'DATETIME(6) NULL');
CALL hb_add_column('bookings', 'room_checked_by', 'VARCHAR(100) NULL');
CALL hb_add_column('bookings', 'room_check_issue', 'BIT NULL');
CALL hb_add_column('bookings', 'room_check_note', 'VARCHAR(300) NULL');

DROP PROCEDURE IF EXISTS hb_add_column;
