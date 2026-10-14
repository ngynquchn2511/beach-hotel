package com.hotel.controller;

import com.hotel.entity.Airport;
import com.hotel.entity.Booking;
import com.hotel.entity.TransferDirection;
import com.hotel.entity.VehicleType;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.BookingService;
import com.hotel.service.ServiceOrderService;
import com.hotel.service.UserMessages;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalTime;

// Khach dat / huy dich vu them (tour lan, dua don san bay) ngay tren trang chi tiet don.
// Giong trang xem don: khach vang lai dung ma truy cap (?token=) trong link, khach da dang nhap dung don cua minh
@Controller
@RequestMapping("/customer/bookings/{id}")
public class CustomerServiceOrderController {

    private final BookingService bookingService;
    private final ServiceOrderService serviceOrderService;
    private final UserMessages userMessages;

    public CustomerServiceOrderController(BookingService bookingService, ServiceOrderService serviceOrderService,
                                          UserMessages userMessages) {
        this.bookingService = bookingService;
        this.serviceOrderService = serviceOrderService;
        this.userMessages = userMessages;
    }

    @PostMapping("/tours")
    public String bookTour(@PathVariable Long id, @RequestParam(required = false) String token,
                           @RequestParam(required = false) Long tourId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tourDate,
                           @RequestParam(required = false) Integer participants,
                           @RequestParam(required = false) String note,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           RedirectAttributes redirectAttributes) {
        try {
            Booking booking = load(id, token, userDetails);
            serviceOrderService.bookTour(booking, tourId, tourDate, participants, note, false);
            redirectAttributes.addFlashAttribute("serviceSuccess", userMessages.get("svc.msg.tourBooked"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("serviceError", userMessages.of(ex));
        }
        return back(id, token);
    }

    @PostMapping("/tours/{orderId}/cancel")
    public String cancelTour(@PathVariable Long id, @PathVariable Long orderId,
                             @RequestParam(required = false) String token,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        try {
            serviceOrderService.cancelTourByCustomer(load(id, token, userDetails), orderId);
            redirectAttributes.addFlashAttribute("serviceSuccess", userMessages.get("svc.msg.cancelled"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("serviceError", userMessages.of(ex));
        }
        return back(id, token);
    }

    @PostMapping("/transfers")
    public String bookTransfer(@PathVariable Long id, @RequestParam(required = false) String token,
                               @RequestParam(required = false) TransferDirection direction,
                               @RequestParam(required = false) Airport airport,
                               @RequestParam(required = false) VehicleType vehicleType,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime time,
                               @RequestParam(required = false) String flightNumber,
                               @RequestParam(required = false) Integer passengers,
                               @RequestParam(required = false) String note,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        try {
            Booking booking = load(id, token, userDetails);
            serviceOrderService.bookTransfer(booking, direction, airport, vehicleType, date, time, flightNumber,
                    passengers, note, false);
            redirectAttributes.addFlashAttribute("serviceSuccess", userMessages.get("svc.msg.transferBooked"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("serviceError", userMessages.of(ex));
        }
        return back(id, token);
    }

    @PostMapping("/transfers/{orderId}/cancel")
    public String cancelTransfer(@PathVariable Long id, @PathVariable Long orderId,
                                 @RequestParam(required = false) String token,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        try {
            serviceOrderService.cancelTransferByCustomer(load(id, token, userDetails), orderId);
            redirectAttributes.addFlashAttribute("serviceSuccess", userMessages.get("svc.msg.cancelled"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("serviceError", userMessages.of(ex));
        }
        return back(id, token);
    }

    private Booking load(Long id, String token, CustomUserDetails userDetails) {
        return bookingService.findForViewing(id, token, userDetails != null ? userDetails.getUser().getId() : null);
    }

    private String back(Long id, String token) {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/customer/bookings/{id}");
        if (token != null && !token.isBlank()) {
            url.queryParam("token", token);
        }
        return "redirect:" + url.buildAndExpand(id).encode().toUriString() + "#dich-vu";
    }
}
