package com.hotel.entity;

public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE;

    public String getVietnameseLabel() {
        return switch (this) {
            case CREATE -> "Thêm mới";
            case UPDATE -> "Cập nhật";
            case DELETE -> "Xóa";
        };
    }
}
