-- AI (Claude) tra loi cau tro ly Song chua hieu: bang cai dat chat_ai_settings (1 dong: bat / tat, tran chi phi thang / ngay,
-- so cau AI moi khach / ngay, gioi han tin moi phut / gio chong spam) + cot ghi trang thai AI / token / chi phi cua tung cau hoi.
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

CREATE TABLE IF NOT EXISTS chat_ai_settings (
    id                 BIGINT NOT NULL PRIMARY KEY,
    enabled            BIT(1) NOT NULL,
    monthly_budget_usd DECIMAL(10, 2) NOT NULL,
    daily_budget_usd   DECIMAL(10, 2) NOT NULL,
    per_user_daily     INT NOT NULL,
    per_minute         INT NOT NULL,
    per_hour           INT NOT NULL,
    updated_at         DATETIME(6) NULL,
    updated_by         VARCHAR(120) NULL
);

CALL hb_add_column('chat_questions', 'ai_status', 'VARCHAR(20) NULL');
CALL hb_add_column('chat_questions', 'ai_input_tokens', 'INT NULL');
CALL hb_add_column('chat_questions', 'ai_output_tokens', 'INT NULL');
CALL hb_add_column('chat_questions', 'ai_cost_micro', 'BIGINT NULL');
DROP PROCEDURE hb_add_column;
