-- Them vai tro Buong phong (HOUSEKEEPER) va Ke toan (ACCOUNTANT) cho tai khoan nhan vien.
-- Cot users.role la ENUM (Hibernate tao tu enum Java) nen phai mo rong danh sach gia tri.
-- Moi truong chay that (DDL_AUTO=validate) phai chay file nay TRUOC khi deploy. Chay lai nhieu lan van an toan.
ALTER TABLE users MODIFY role ENUM ('CUSTOMER', 'STAFF', 'ADMIN', 'HOUSEKEEPER', 'ACCOUNTANT') NOT NULL;
