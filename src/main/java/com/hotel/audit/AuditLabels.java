package com.hotel.audit;

import java.util.Map;

// Ten tieng Viet cua doi tuong / truong du lieu de hien thi trong nhat ky thao tac
public final class AuditLabels {

    private static final Map<String, String> ENTITY_LABELS = Map.ofEntries(
            Map.entry("Booking", "Đơn đặt phòng"),
            Map.entry("Room", "Phòng"),
            Map.entry("RoomType", "Loại phòng"),
            Map.entry("RoomImage", "Ảnh phòng"),
            Map.entry("Combo", "Combo dịch vụ"),
            Map.entry("DiscountCode", "Mã giảm giá"),
            Map.entry("Payment", "Thanh toán"),
            Map.entry("Review", "Đánh giá"),
            Map.entry("BookingCharge", "Phụ phí"),
            Map.entry("User", "Tài khoản"),
            Map.entry("PricingSettings", "Cài đặt giá & đặt cọc"),
            Map.entry("SpecialRate", "Giá ngày lễ"),
            Map.entry("BookingGuest", "Khách lưu trú"),
            Map.entry("ReviewMedia", "Ảnh / video đánh giá")
    );

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("fullName", "Họ tên"),
            Map.entry("email", "Email"),
            Map.entry("password", "Mật khẩu"),
            Map.entry("phoneNumber", "Số điện thoại"),
            Map.entry("role", "Vai trò"),
            Map.entry("customerType", "Loại khách hàng"),
            Map.entry("locked", "Khóa tài khoản"),
            Map.entry("resetToken", "Mã đặt lại mật khẩu"),
            Map.entry("resetTokenExpiry", "Hạn đặt lại mật khẩu"),
            Map.entry("customer", "Khách hàng"),
            Map.entry("room", "Phòng"),
            Map.entry("roomNumber", "Số phòng"),
            Map.entry("roomType", "Loại phòng"),
            Map.entry("checkInDate", "Ngày nhận phòng"),
            Map.entry("checkOutDate", "Ngày trả phòng"),
            Map.entry("checkInTime", "Giờ nhận phòng"),
            Map.entry("checkOutTime", "Giờ trả phòng"),
            Map.entry("checkInTimeChanged", "Đã đổi giờ"),
            Map.entry("numberOfGuests", "Số khách"),
            Map.entry("guestName", "Tên người nhận phòng"),
            Map.entry("guestPhone", "SĐT người nhận phòng"),
            Map.entry("guestEmail", "Email người nhận phòng"),
            Map.entry("totalAmount", "Tổng tiền"),
            Map.entry("status", "Trạng thái"),
            Map.entry("combo", "Combo"),
            Map.entry("discountCode", "Mã giảm giá"),
            Map.entry("discountAmount", "Tiền giảm"),
            Map.entry("name", "Tên"),
            Map.entry("code", "Mã"),
            Map.entry("description", "Mô tả"),
            Map.entry("price", "Giá"),
            Map.entry("basePrice", "Giá cơ bản"),
            Map.entry("maxGuests", "Số khách tối đa"),
            Map.entry("area", "Diện tích"),
            Map.entry("amenities", "Tiện nghi"),
            Map.entry("imageUrl", "Ảnh"),
            Map.entry("url", "Đường dẫn tệp"),
            Map.entry("mediaType", "Loại tệp"),
            Map.entry("sortOrder", "Thứ tự"),
            Map.entry("nameEn", "Tên (tiếng Anh)"),
            Map.entry("descriptionEn", "Mô tả (tiếng Anh)"),
            Map.entry("amenitiesEn", "Tiện nghi (tiếng Anh)"),
            Map.entry("active", "Đang áp dụng"),
            Map.entry("discountType", "Loại giảm"),
            Map.entry("discountValue", "Giá trị giảm"),
            Map.entry("applicableCustomerType", "Áp dụng cho"),
            Map.entry("booking", "Đơn đặt phòng"),
            Map.entry("amount", "Số tiền"),
            Map.entry("paymentMethod", "Phương thức"),
            Map.entry("paymentDate", "Ngày thanh toán"),
            Map.entry("rating", "Số sao"),
            Map.entry("comment", "Nhận xét"),
            Map.entry("hidden", "Ẩn"),
            Map.entry("type", "Loại"),
            Map.entry("createdAt", "Ngày tạo"),
            Map.entry("roomAmount", "Tiền phòng"),
            Map.entry("depositAmount", "Tiền cọc"),
            Map.entry("depositDeadline", "Hạn chuyển cọc"),
            Map.entry("depositPaidAt", "Ngày nhận cọc"),
            Map.entry("weekendSurchargePercent", "Phụ thu cuối tuần (%)"),
            Map.entry("depositPercent", "Tỷ lệ đặt cọc (%)"),
            Map.entry("depositDeadlineHours", "Hạn chuyển cọc (giờ)"),
            Map.entry("startDate", "Từ ngày"),
            Map.entry("endDate", "Đến ngày"),
            Map.entry("surchargePercent", "Phụ thu (%)"),
            Map.entry("dateOfBirth", "Ngày sinh"),
            Map.entry("gender", "Giới tính"),
            Map.entry("idType", "Loại giấy tờ"),
            Map.entry("idNumber", "Số giấy tờ"),
            Map.entry("nationality", "Quốc tịch"),
            Map.entry("address", "Nơi thường trú"),
            Map.entry("accessToken", "Mã xem đơn")
    );

    private AuditLabels() {
    }

    public static Map<String, String> entityLabels() {
        return ENTITY_LABELS;
    }

    public static String entityLabel(String entityType) {
        return ENTITY_LABELS.getOrDefault(entityType, entityType);
    }

    public static String fieldLabel(String field) {
        return FIELD_LABELS.getOrDefault(field, field);
    }
}
