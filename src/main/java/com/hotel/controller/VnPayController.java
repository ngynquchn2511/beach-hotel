package com.hotel.controller;

import com.hotel.entity.Booking;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.BookingService;
import com.hotel.service.UserMessages;
import com.hotel.service.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

// Dat coc online qua VNPay: chu don (dang nhap) hoac khach vang lai co ?token= bam nut tren trang don
@Controller
public class VnPayController {

    private final BookingService bookingService;
    private final VnPayService vnPayService;
    private final UserMessages messages;

    public VnPayController(BookingService bookingService, VnPayService vnPayService, UserMessages messages) {
        this.bookingService = bookingService;
        this.vnPayService = vnPayService;
        this.messages = messages;
    }

    @PostMapping("/customer/bookings/{id}/vnpay")
    public String start(@PathVariable Long id, @RequestParam(required = false) String token,
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.findForViewing(id, token,
                userDetails != null ? userDetails.getUser().getId() : null);
        try {
            String returnUrl = ServletUriComponentsBuilder.fromContextPath(request)
                    .path("/payment/vnpay/return").build().toUriString();
            boolean english = "en".equals(LocaleContextHolder.getLocale().getLanguage());
            return "redirect:" + vnPayService.createDepositPaymentUrl(booking, clientIp(request), returnUrl, english);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", messages.of(ex));
            return "redirect:" + bookingPage(booking);
        }
    }

    // VNPay dua khach quay lai sau khi tra tien (hoac bam huy). Chu ky da duoc kiem tra truoc khi tin bat ky tham so nao
    @GetMapping("/payment/vnpay/return")
    public String paymentReturn(@RequestParam Map<String, String> params, RedirectAttributes redirectAttributes) {
        VnPayService.Result result = vnPayService.handleCallback(params);
        if (result.outcome() == VnPayService.Outcome.INVALID_SIGNATURE || result.bookingId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", messages.get("vnpay.invalid"));
            return "redirect:/";
        }
        String key = switch (result.outcome()) {
            case SUCCESS, ALREADY_PAID -> "vnpay.success";
            case PAYMENT_FAILED -> "vnpay.failed";
            case PAID_BUT_ROOM_TAKEN, NOT_AWAITING -> "vnpay.paidNeedsStaff";
            default -> "vnpay.invalid";
        };
        boolean ok = result.outcome() == VnPayService.Outcome.SUCCESS || result.outcome() == VnPayService.Outcome.ALREADY_PAID;
        redirectAttributes.addFlashAttribute(ok ? "successMessage" : "errorMessage", messages.get(key));
        try {
            return "redirect:" + bookingPage(bookingService.findById(result.bookingId()));
        } catch (BusinessException ex) {
            return "redirect:/";
        }
    }

    // IPN: VNPay goi thang tu server cua ho de bao ket qua (khong phu thuoc trinh duyet cua khach).
    // Ma tra loi theo tai lieu VNPay: 00 da nhan, 01 khong thay don, 02 don da xac nhan, 04 sai so tien, 97 sai chu ky
    @GetMapping("/payment/vnpay/ipn")
    @ResponseBody
    public Map<String, String> ipn(@RequestParam Map<String, String> params) {
        VnPayService.Result result = vnPayService.handleCallback(params);
        return switch (result.outcome()) {
            case SUCCESS, PAYMENT_FAILED, PAID_BUT_ROOM_TAKEN -> Map.of("RspCode", "00", "Message", "Confirm Success");
            case ALREADY_PAID, NOT_AWAITING -> Map.of("RspCode", "02", "Message", "Order already confirmed");
            case NOT_FOUND -> Map.of("RspCode", "01", "Message", "Order not found");
            case WRONG_AMOUNT -> Map.of("RspCode", "04", "Message", "Invalid amount");
            case INVALID_SIGNATURE -> Map.of("RspCode", "97", "Message", "Invalid signature");
        };
    }

    // Khach vang lai khong dang nhap -> kem ma truy cap de mo lai duoc trang don
    private static String bookingPage(Booking booking) {
        return "/customer/bookings/" + booking.getId()
                + (booking.getAccessToken() != null ? "?token=" + booking.getAccessToken() : "");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
