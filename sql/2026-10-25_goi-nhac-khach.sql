-- Goi dien nhac khach (CallReminder): moi lan le tan goi nhac khach sap den / sap het han coc va ket qua.
-- Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS call_reminders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    purpose VARCHAR(10) NOT NULL,
    result VARCHAR(20) NOT NULL,
    note VARCHAR(300) NULL,
    staff_name VARCHAR(100) NULL,
    called_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_call_reminder_booking (booking_id),
    CONSTRAINT fk_call_reminder_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
