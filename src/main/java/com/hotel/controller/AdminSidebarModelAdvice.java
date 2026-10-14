package com.hotel.controller;

import com.hotel.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Bom san so don moi cho sidebar admin (hien cham do o muc "Dat phong / Check-in-out")
// tren moi trang co sidebar - khong ap dung cho trang dang nhap (AdminAuthController)
@ControllerAdvice(assignableTypes = {
        AdminController.class,
        AdminBookingController.class,
        AdminRoomCalendarController.class,
        AdminWalkInBookingController.class,
        AdminComboController.class,
        AdminCustomerController.class,
        AdminDiscountCodeController.class,
        AdminRoomController.class,
        AdminRoomTypeController.class,
        AdminStaffAccountController.class,
        AdminAuditLogController.class,
        AdminReviewController.class,
        AdminPricingController.class,
        AdminGuestRegistrationController.class
})
public class AdminSidebarModelAdvice {

    private final BookingService bookingService;

    public AdminSidebarModelAdvice(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @ModelAttribute("newBookingCount")
    public long newBookingCount() {
        return bookingService.countNewBookings();
    }

    // Duong dan hien tai (bo context path) de sidebar to dam muc dang mo
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
