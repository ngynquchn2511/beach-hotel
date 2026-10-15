package com.hotel.controller;

import com.hotel.service.BookingService;
import com.hotel.service.ContactMessageService;
import com.hotel.service.FrontDeskService;
import com.hotel.service.HousekeepingService;
import com.hotel.service.MaintenanceService;
import com.hotel.service.RoomServiceService;
import com.hotel.service.ServiceOrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Bom san so don moi cho sidebar admin (hien cham do o muc "Dat phong / Check-in-out")
// tren moi trang co sidebar - khong ap dung cho trang dang nhap (AdminAuthController)
@ControllerAdvice(assignableTypes = {
        AdminController.class,
        AdminTodayController.class,
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
        AdminGuestRegistrationController.class,
        AdminDivingTourController.class,
        AdminServiceOrderController.class,
        AdminContactMessageController.class,
        AdminHousekeepingController.class,
        AdminIcalController.class,
        AdminRoomServiceController.class,
        AdminRoomAlertController.class,
        AdminMaintenanceController.class,
        AdminBookingGroupController.class,
        AdminForecastController.class,
        AdminGiftVoucherController.class,
        AdminLostItemController.class,
        AdminCompanyInvoiceController.class,
        AdminCashShiftController.class,
        AdminFacilityController.class
})
public class AdminSidebarModelAdvice {

    private final BookingService bookingService;
    private final ServiceOrderService serviceOrderService;
    private final FrontDeskService frontDeskService;
    private final ContactMessageService contactMessageService;
    private final HousekeepingService housekeepingService;
    private final RoomServiceService roomServiceService;
    private final MaintenanceService maintenanceService;
    private com.hotel.service.FacilityReservationService facilityService;

    @org.springframework.beans.factory.annotation.Autowired
    void setFacilityService(com.hotel.service.FacilityReservationService facilityService) {
        this.facilityService = facilityService;
    }

    // Lich spa / ban nha hang cho le tan xac nhan
    @ModelAttribute("pendingFacilityCount")
    public long pendingFacilityCount() {
        return facilityService != null ? facilityService.countPending() : 0;
    }

    public AdminSidebarModelAdvice(BookingService bookingService, ServiceOrderService serviceOrderService,
                                   FrontDeskService frontDeskService, ContactMessageService contactMessageService,
                                   HousekeepingService housekeepingService, RoomServiceService roomServiceService,
                                   MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
        this.housekeepingService = housekeepingService;
        this.roomServiceService = roomServiceService;
        this.bookingService = bookingService;
        this.serviceOrderService = serviceOrderService;
        this.frontDeskService = frontDeskService;
        this.contactMessageService = contactMessageService;
    }

    // So phieu bao hong chua xong - hien so o muc "Bao tri phong"
    @ModelAttribute("openMaintenanceCount")
    public long openMaintenanceCount() {
        return maintenanceService.countOpen();
    }

    // So yeu cau goi mon len phong dang cho giao - hien so o muc "Goi len phong"
    @ModelAttribute("pendingRoomServiceCount")
    public long pendingRoomServiceCount() {
        return roomServiceService.countPending();
    }

    // So phong chua sach (cho don + dang don) - hien so o muc "Don phong"
    @ModelAttribute("dirtyRoomCount")
    public long dirtyRoomCount() {
        return housekeepingService.countDirty();
    }

    // So tin lien he khach gui ma chua ai xu ly - hien so o muc "Hop thu lien he"
    @ModelAttribute("newContactCount")
    public long newContactCount() {
        return contactMessageService.countNew();
    }

    // So khach con phai don hom nay (chua nhan phong) - hien o muc "Hom nay"
    @ModelAttribute("arrivalsLeftToday")
    public long arrivalsLeftToday() {
        return frontDeskService.arrivalsLeftToday(java.time.LocalDate.now());
    }

    // So dich vu them (tour lan / xe dua don) dang cho le tan xac nhan - hien so o muc "Dich vu them"
    @ModelAttribute("pendingServiceCount")
    public long pendingServiceCount() {
        return serviceOrderService.countPending();
    }

    // So don da huy dang cho hoan coc - hien so canh muc "Dat phong / Check-in-out"
    @ModelAttribute("pendingRefundCount")
    public long pendingRefundCount() {
        return bookingService.countPendingRefunds();
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
