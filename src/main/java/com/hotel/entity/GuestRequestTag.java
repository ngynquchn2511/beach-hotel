package com.hotel.entity;

// Yeu cau dac biet khach chon nhanh khi dat phong (luu dang ma, cach nhau dau phay, tren don)
public enum GuestRequestTag {
    HIGH_FLOOR("Tầng cao"),
    QUIET_ROOM("Phòng yên tĩnh"),
    EXTRA_BED("Giường phụ"),
    BABY_COT("Cũi em bé"),
    TWIN_BEDS("2 giường đơn"),
    CELEBRATION("Kỷ niệm / sinh nhật"),
    ALLERGY("Dị ứng"),
    LATE_ARRIVAL("Đến muộn"),
    ACCESSIBLE("Hỗ trợ đi lại");

    private final String vietnameseLabel;

    GuestRequestTag(String vietnameseLabel) {
        this.vietnameseLabel = vietnameseLabel;
    }

    public String getVietnameseLabel() {
        return vietnameseLabel;
    }

    // Buong phong can chuan bi truoc trong phong
    public boolean isForHousekeeping() {
        return this == EXTRA_BED || this == BABY_COT || this == TWIN_BEDS || this == CELEBRATION || this == ALLERGY || this == ACCESSIBLE;
    }
}
