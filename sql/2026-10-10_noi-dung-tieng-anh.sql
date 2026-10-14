-- Song ngu noi dung admin nhap (nut VI/EN o trang khach): them cot ban tieng Anh, bo trong = dung ban tieng Viet.
-- May dev (ddl-auto=update) Hibernate tu them cot khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc.
ALTER TABLE room_types ADD COLUMN IF NOT EXISTS name_en VARCHAR(100) NULL;
ALTER TABLE room_types ADD COLUMN IF NOT EXISTS description_en VARCHAR(1000) NULL;
ALTER TABLE room_types ADD COLUMN IF NOT EXISTS amenities_en VARCHAR(500) NULL;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS description_en VARCHAR(1000) NULL;
ALTER TABLE combos ADD COLUMN IF NOT EXISTS name_en VARCHAR(150) NULL;
ALTER TABLE combos ADD COLUMN IF NOT EXISTS description_en VARCHAR(1000) NULL;
ALTER TABLE discount_codes ADD COLUMN IF NOT EXISTS description_en VARCHAR(500) NULL;
ALTER TABLE special_rates ADD COLUMN IF NOT EXISTS name_en VARCHAR(100) NULL;
