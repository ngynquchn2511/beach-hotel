package com.hotel.controller;

import com.hotel.entity.Airport;
import com.hotel.entity.Booking;
import com.hotel.entity.DivingTour;
import com.hotel.entity.VehicleType;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.BookingService;
import com.hotel.service.DivingTourService;
import com.hotel.service.ServiceOrderService;
import com.hotel.service.TransferRateService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

// Trang gioi thieu dich vu them (tour lan bien, dua don san bay) - ai cung xem duoc.
// Dat dich vu thuc hien o trang chi tiet don phong (dich vu luon gan voi 1 don)
@Controller
public class ServicesController {

    private final DivingTourService divingTourService;
    private final TransferRateService transferRateService;
    private final BookingService bookingService;
    private final ServiceOrderService serviceOrderService;

    public ServicesController(DivingTourService divingTourService, TransferRateService transferRateService,
                              BookingService bookingService, ServiceOrderService serviceOrderService) {
        this.divingTourService = divingTourService;
        this.transferRateService = transferRateService;
        this.bookingService = bookingService;
        this.serviceOrderService = serviceOrderService;
    }

    private com.hotel.service.FacilityReservationService facilityService;
    private com.hotel.service.SpaPackageService spaPackageService;

    @org.springframework.beans.factory.annotation.Autowired
    void setFacility(com.hotel.service.FacilityReservationService facilityService, com.hotel.service.SpaPackageService spaPackageService) {
        this.facilityService = facilityService;
        this.spaPackageService = spaPackageService;
    }

    // So tour moi trang o muc "Tour lan bien" (luoi 3 cot)
    static final int TOURS_PER_PAGE = 6;

    @GetMapping("/services")
    public String services(@RequestParam(defaultValue = "1") int page, Model model) {
        Pager<DivingTour> tourPager = Pager.of(divingTourService.findActive(), page, TOURS_PER_PAGE);
        model.addAttribute("tourPager", tourPager);
        model.addAttribute("tours", tourPager.items());
        model.addAttribute("airports", Airport.values());
        model.addAttribute("vehicles", VehicleType.values());
        model.addAttribute("rates", transferRateService.priceTable());
        model.addAttribute("tourLeadHours", ServiceOrderService.TOUR_LEAD_HOURS);
        model.addAttribute("transferLeadHours", ServiceOrderService.TRANSFER_LEAD_HOURS);
        // Spa & nha hang: goi dang ban, diem khach cham, gop y tieu bieu
        if (facilityService != null && spaPackageService != null) {
            model.addAttribute("spaPackages", spaPackageService.active());
            model.addAttribute("spaRatings", facilityService.spaRatings());
            model.addAttribute("restaurantRating", facilityService.restaurantRating());
            model.addAttribute("testimonials", facilityService.featuredFeedback(3));
            var spaSlots = com.hotel.service.FacilityReservationService.SPA_SLOTS;
            model.addAttribute("spaHours", spaSlots.get(0) + " - " + spaSlots.get(spaSlots.size() - 1));
        }
        return "services";
    }

    // Nut "Dat tour nay" o trang /services (thuoc /customer/** nen bat buoc dang nhap, xong quay lai day).
    // Khach co 1 don con dat dich vu duoc -> mo thang form dat tour cua don do (chon san tour);
    // nhieu don -> chon don; chua co don nao -> goi y dat phong
    @GetMapping("/customer/services/tours/{tourId}")
    public String bookTour(@PathVariable Long tourId, @AuthenticationPrincipal CustomUserDetails userDetails,
                           @RequestParam(defaultValue = "1") int page, Model model) {
        DivingTour tour = divingTourService.findActive().stream()
                .filter(t -> t.getId().equals(tourId))
                .findFirst().orElse(null);
        if (tour == null) {
            return "redirect:/services#tour-lan";
        }
        LocalDate today = LocalDate.now();
        List<Booking> bookings = bookingService.findAllForCustomer(userDetails.getUser().getId()).stream()
                .filter(serviceOrderService::canOrderServices)
                .filter(b -> !b.getCheckOutDate().isBefore(today))
                .sorted(java.util.Comparator.comparing(Booking::getCheckInDate))
                .toList();
        if (bookings.size() == 1) {
            return "redirect:/customer/bookings/" + bookings.get(0).getId() + "?tourId=" + tourId + "#dich-vu";
        }
        model.addAttribute("tour", tour);
        Pager<Booking> pager = Pager.of(bookings, page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("pager", pager);
        model.addAttribute("bookings", pager.items());
        return "customer/choose-booking-for-tour";
    }
}
