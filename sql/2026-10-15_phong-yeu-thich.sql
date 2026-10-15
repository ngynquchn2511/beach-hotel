-- Phong yeu thich cua khach (nut trai tim o trang phong).
-- May dev (ddl-auto=update) Hibernate tu tao bang khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
-- ON DELETE CASCADE: xoa phong / tai khoan thi tu xoa cac dong yeu thich lien quan.
CREATE TABLE IF NOT EXISTS favorite_rooms (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    room_id     BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite_rooms_user_room (user_id, room_id),
    KEY idx_favorite_rooms_room (room_id),
    CONSTRAINT fk_favorite_rooms_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_rooms_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
