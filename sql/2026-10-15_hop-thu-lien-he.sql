-- Hop thu lien he: yeu cau tu van khach gui tu form "Lien he" o trang gioi thieu.
-- May dev (ddl-auto=update) Hibernate tu tao bang khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS contact_messages (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    full_name   VARCHAR(100)  NOT NULL,
    email       VARCHAR(150)  NOT NULL,
    phone       VARCHAR(20)   NULL,
    message     VARCHAR(2000) NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    reply       VARCHAR(2000) NULL,
    replied_at  DATETIME(6)   NULL,
    handled_by  VARCHAR(100)  NULL,
    handled     BIT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_contact_messages_email_created (email, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
