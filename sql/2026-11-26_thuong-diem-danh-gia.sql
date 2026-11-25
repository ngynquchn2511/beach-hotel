-- Thuong diem khi khach viet danh gia (them anh / video duoc nhieu hon): cot reviews.reward_points = so diem da cong
-- (0 / NULL = chua cong hoac da thu hoi khi quan tri an danh gia). Lich su diem dung point_transactions.type REVIEW / REVOKE
-- (cot VARCHAR(10) thi khong can sua; Hibernate tao ENUM thi them 2 gia tri moi). Moi truong chay that (DDL_AUTO=validate) phai chay TRUOC khi deploy. Chay lai van an toan.
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

DROP PROCEDURE IF EXISTS hb_point_type //
CREATE PROCEDURE hb_point_type()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'point_transactions' AND COLUMN_NAME = 'type'
                 AND DATA_TYPE = 'enum' AND COLUMN_TYPE NOT LIKE '%REVOKE%') THEN
        ALTER TABLE point_transactions MODIFY type ENUM('EARN','REDEEM','REFUND','REVIEW','REVOKE') NOT NULL;
    END IF;
END //
DELIMITER ;

CALL hb_add_column('reviews', 'reward_points', 'INT NULL');
CALL hb_point_type();
DROP PROCEDURE hb_point_type;
DROP PROCEDURE hb_add_column;
