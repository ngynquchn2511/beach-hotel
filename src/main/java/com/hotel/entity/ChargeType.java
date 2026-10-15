package com.hotel.entity;

// Loai phu phi phat sinh khi khach o (tinh luc tra phong)
public enum ChargeType {
    MINIBAR,
    LAUNDRY,
    DAMAGE,
    LATE_CHECKOUT,
    EARLY_CHECKIN,
    // Goi do an / do uong len phong (minibar, giat ui dung loai rieng o tren)
    ROOM_SERVICE,
    // 2 loai duoi tao tu dong khi le tan xac nhan dich vu them - khong chon tay o form phu phi
    DIVING_TOUR,
    AIRPORT_TRANSFER,
    // Tao tu dong khi le tan xac nhan lich spa
    SPA,
    OTHER;

    public String getVietnameseLabel() {
        return switch (this) {
            case MINIBAR -> "Minibar";
            case LAUNDRY -> "Giặt ủi";
            case DAMAGE -> "Hư hỏng / mất đồ";
            case LATE_CHECKOUT -> "Trả phòng muộn";
            case EARLY_CHECKIN -> "Nhận phòng sớm";
            case ROOM_SERVICE -> "Dịch vụ lên phòng";
            case DIVING_TOUR -> "Tour lặn biển";
            case AIRPORT_TRANSFER -> "Đưa đón sân bay";
            case SPA -> "Spa";
            case OTHER -> "Khác";
        };
    }

    public boolean isServiceCharge() {
        return this == DIVING_TOUR || this == AIRPORT_TRANSFER || this == SPA;
    }

    // Cac loai le tan duoc chon tay khi them phu phi o man hinh check-out
    public static java.util.List<ChargeType> manualTypes() {
        return java.util.Arrays.stream(values()).filter(t -> !t.isServiceCharge()).toList();
    }
}
