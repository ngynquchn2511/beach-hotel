package com.hotel.entity;

// San bay resort co xe dua don (ten song ngu cho trang khach o messages*.properties: airport.VAN_DON...)
public enum Airport {
    VAN_DON,
    NOI_BAI,
    CAT_BI;

    public String getVietnameseLabel() {
        return switch (this) {
            case VAN_DON -> "Sân bay Vân Đồn (VDO)";
            case NOI_BAI -> "Sân bay Nội Bài (HAN)";
            case CAT_BI -> "Sân bay Cát Bi - Hải Phòng (HPH)";
        };
    }
}
