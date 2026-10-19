-- Dong bo 2 chieu voi Booking.com / Airbnb qua iCal: link lich cua kenh (ota_feeds), luot dat doc tu kenh (ota_blocks),
-- kenh dat moi "OTA" cho don ghi nhan tu kenh. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS ota_feeds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    channel VARCHAR(10) NOT NULL,
    url VARCHAR(600) NOT NULL,
    active BIT(1) NOT NULL,
    last_sync_at DATETIME(6) NULL,
    last_ok BIT(1) NULL,
    last_message VARCHAR(300) NULL,
    event_count INT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ota_feed_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ota_blocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    feed_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    uid VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    summary VARCHAR(200) NULL,
    booking_id BIGINT NULL,
    removed_at DATETIME(6) NULL,
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ota_block_uid UNIQUE (feed_id, uid),
    KEY idx_ota_block_room_dates (room_id, start_date, end_date),
    CONSTRAINT fk_ota_block_feed FOREIGN KEY (feed_id) REFERENCES ota_feeds (id),
    CONSTRAINT fk_ota_block_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_ota_block_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE bookings MODIFY channel ENUM('ONLINE','WALK_IN','OTA') NULL;
