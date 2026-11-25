-- Thong ke tro ly "Song": luu cuoc tro chuyen (chat_sessions) + tung cau hoi (chat_questions) de quan tri xem
-- khach hoi gi nhieu nhat, cau nao Song chua hieu, ty le "da giai quyet" sau cau hoi lai 30 giay, gop y khach chua hai long.
-- Moi truong chay that (DDL_AUTO=validate) phai chay TRUOC khi deploy ban moi. Chay lai nhieu lan van an toan.

CREATE TABLE IF NOT EXISTS chat_sessions (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id      BIGINT NULL,
    locale           VARCHAR(5) NULL,
    started_at       DATETIME(6) NOT NULL,
    last_at          DATETIME(6) NOT NULL,
    question_count   INT NOT NULL,
    unanswered_count INT NOT NULL,
    outcome          VARCHAR(20) NOT NULL,
    check_asked_at   DATETIME(6) NULL,
    reason           VARCHAR(20) NULL,
    feedback         VARCHAR(500) NULL,
    feedback_at      DATETIME(6) NULL,
    handled_at       DATETIME(6) NULL,
    handled_by_id    BIGINT NULL,
    staff_note       VARCHAR(300) NULL,
    INDEX idx_chat_sessions_started (started_at),
    CONSTRAINT fk_chat_sessions_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT fk_chat_sessions_handled_by FOREIGN KEY (handled_by_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS chat_questions (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    asked_at   DATETIME(6) NOT NULL,
    question   VARCHAR(500) NOT NULL,
    topics     VARCHAR(200) NULL,
    room_types VARCHAR(200) NULL,
    answered   BIT(1) NOT NULL,
    answer     VARCHAR(2000) NULL,
    INDEX idx_chat_questions_asked (asked_at),
    CONSTRAINT fk_chat_questions_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id)
);
