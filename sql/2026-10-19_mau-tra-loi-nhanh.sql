-- Mau tra loi nhanh cho le tan (QuickReply). Chay lai nhieu lan van an toan.
-- Noi dung mac dinh duoc ung dung tu tao khi bang trong (QuickReplyService.seedDefaults).
CREATE TABLE IF NOT EXISTS quick_replies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(80) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    sort_order INT NOT NULL,
    active BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
