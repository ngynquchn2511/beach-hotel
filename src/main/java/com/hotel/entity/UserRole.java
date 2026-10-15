package com.hotel.entity;

public enum UserRole {
    CUSTOMER,
    STAFF,
    ADMIN,
    // Buong phong: chi don phong, bao hong, do that lac
    HOUSEKEEPER,
    // Ke toan: doanh thu, so quy ca (xem), hoa don cong ty
    ACCOUNTANT;

    public String getVietnameseLabel() {
        return switch (this) {
            case CUSTOMER -> "Khách hàng";
            case STAFF -> "Nhân viên";
            case ADMIN -> "Quản trị viên";
            case HOUSEKEEPER -> "Buồng phòng";
            case ACCOUNTANT -> "Kế toán";
        };
    }

    // Dang nhap cong quan tri /admin
    public boolean isBackOffice() {
        return this != CUSTOMER;
    }

    // Trang dau tien sau khi dang nhap
    public String adminHome() {
        return switch (this) {
            case HOUSEKEEPER -> "/admin/housekeeping";
            case ACCOUNTANT -> "/admin/company-invoices";
            default -> "/admin/dashboard";
        };
    }
}
