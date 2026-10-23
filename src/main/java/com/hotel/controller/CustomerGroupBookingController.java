package com.hotel.controller;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingGroup;
import com.hotel.entity.Room;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.BookingService;
import com.hotel.service.CustomerManagementService;
import com.hotel.service.PaymentService;
import com.hotel.service.PricingService;
import com.hotel.service.PromotionService;
import com.hotel.service.RoomService;
import com.hotel.service.StayTimePolicy;
import com.hotel.service.UserMessages;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Dat nhieu phong 1 lan (gia dinh / doan) - khong bat buoc dang nhap, giong dat 1 phong
@Controller
public class CustomerGroupBookingController {

    private final BookingService bookingService;
    private final RoomService roomService;
    private final PricingService pricingService;
    private final PromotionService promotionService;
    private final PaymentService paymentService;
    private final CustomerManagementService customerManagementService;
    private final UserMessages userMessages;

    public CustomerGroupBookingController(BookingService bookingService, RoomService roomService, PricingService pricingService,
                                          PromotionService promotionService, PaymentService paymentService,
                                          CustomerManagementService customerManagementService, UserMessages userMessages) {
        this.bookingService = bookingService;
        this.roomService = roomService;
        this.pricingService = pricingService;
        this.promotionService = promotionService;
        this.paymentService = paymentService;
        this.customerManagementService = customerManagementService;
        this.userMessages = userMessages;
    }

    // 1 phong tren trang xac nhan: tien phong (da tru khuyen mai tu dong va gia thanh vien neu co)
    public record Line(Room room, BigDecimal roomAmount, BigDecimal promotionAmount, BigDecimal memberAmount, BigDecimal total) {
    }

    @GetMapping("/customer/bookings/group/new")
    public String confirmForm(@RequestParam(required = false) List<Long> roomIds,
                              @RequestParam LocalDate checkIn, @RequestParam LocalDate checkOut,
                              @AuthenticationPrincipal CustomUserDetails userDetails, Model model,
                              RedirectAttributes redirectAttributes) {
        List<Long> ids = roomIds == null ? List.of() : roomIds.stream().distinct().toList();
        if (ids.size() < BookingService.MIN_GROUP_ROOMS || ids.size() > BookingService.MAX_GROUP_ROOMS) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.get("group.error.count",
                    BookingService.MIN_GROUP_ROOMS, BookingService.MAX_GROUP_ROOMS));
            return "redirect:/customer/rooms?checkIn=" + checkIn + "&checkOut=" + checkOut;
        }
        fillModel(model, ids, checkIn, checkOut, userDetails);
        if (userDetails != null) {
            model.addAttribute("guestName", userDetails.getUser().getFullName());
            model.addAttribute("guestPhone", userDetails.getUser().getPhoneNumber());
            model.addAttribute("guestEmail", userDetails.getUser().getEmail());
        }
        return "customer/group-confirm";
    }

    @PostMapping("/customer/bookings/group/new")
    public String create(@RequestParam List<Long> roomIds, @RequestParam LocalDate checkIn, @RequestParam LocalDate checkOut,
                         @RequestParam(required = false) LocalTime checkInTime, @RequestParam(required = false) LocalTime checkOutTime,
                         @RequestParam(required = false) String guestName, @RequestParam(required = false) String guestPhone,
                         @RequestParam(required = false) String guestEmail,
                         @RequestParam(required = false) String discountCode, @RequestParam(required = false) String giftCode,
                         @RequestParam(required = false) Integer pointsToUse, @RequestParam(required = false) Boolean nonRefundable,
                         @RequestParam Map<String, String> params,
                         @AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Map<Long, Integer> guests = new HashMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("guests_") && v != null && !v.isBlank()) {
                try {
                    guests.put(Long.valueOf(k.substring(7)), Integer.valueOf(v.trim()));
                } catch (NumberFormatException ignored) {
                    // bo qua gia tri la
                }
            }
        });
        try {
            // Dat nhieu phong bat buoc dang nhap (SecurityConfig) - gan vao tai khoan dang dung
            User customer = userDetails.getUser();
            BookingGroup group = bookingService.createGroupBooking(customer, roomIds, checkIn, checkOut,
                    checkInTime != null ? checkInTime : StayTimePolicy.STANDARD_CHECK_IN,
                    checkOutTime != null ? checkOutTime : StayTimePolicy.STANDARD_CHECK_OUT,
                    guests, guestName, guestPhone, guestEmail, discountCode,
                    pointsToUse != null ? pointsToUse : 0, giftCode, true,
                    Boolean.TRUE.equals(nonRefundable));
            return "redirect:/customer/bookings/group/" + group.getId() + "?token=" + group.getAccessToken();
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", userMessages.of(ex));
            fillModel(model, roomIds.stream().distinct().toList(), checkIn, checkOut, userDetails);
            model.addAttribute("discountCode", discountCode);
            model.addAttribute("giftCode", giftCode);
            model.addAttribute("pointsToUse", pointsToUse);
            model.addAttribute("nonRefundableChosen", Boolean.TRUE.equals(nonRefundable));
            model.addAttribute("guestName", guestName);
            model.addAttribute("guestPhone", guestPhone);
            model.addAttribute("guestEmail", guestEmail);
            return "customer/group-confirm";
        }
    }

    private com.hotel.service.VnPayService vnPayService;

    @org.springframework.beans.factory.annotation.Autowired
    void setVnPayService(com.hotel.service.VnPayService vnPayService) {
        this.vnPayService = vnPayService;
    }

    @GetMapping("/customer/bookings/group/{id}")
    public String view(@PathVariable Long id, @RequestParam(required = false) String token,
                       @AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        BookingGroup group = bookingService.findGroupForViewing(id, token, userDetails != null ? userDetails.getUser().getId() : null);
        List<Booking> bookings = bookingService.groupBookings(id);
        BigDecimal total = bookings.stream().filter(b -> b.getStatus() != com.hotel.entity.BookingStatus.CANCELLED)
                .map(Booking::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deposit = bookings.stream().filter(Booking::isAwaitingDeposit)
                .map(Booking::getDepositAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("group", group);
        model.addAttribute("bookings", bookings);
        model.addAttribute("total", total);
        model.addAttribute("deposit", deposit);
        model.addAttribute("accessToken", token);
        model.addAttribute("canCancel", bookings.stream().anyMatch(bookingService::canModify));
        if (deposit.signum() > 0) {
            model.addAttribute("depositQrUrl", paymentService.buildGroupDepositQrUrl(deposit, group.getCode()));
            model.addAttribute("bankId", paymentService.getBankId());
            model.addAttribute("accountNo", paymentService.getAccountNo());
            model.addAttribute("accountName", paymentService.getAccountName());
            model.addAttribute("vnpayEnabled", vnPayService != null && vnPayService.isEnabled());
        }
        return "customer/group-booking";
    }

    @PostMapping("/customer/bookings/group/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String token,
                         @AuthenticationPrincipal CustomUserDetails userDetails, RedirectAttributes redirectAttributes) {
        BookingGroup group = bookingService.findGroupForViewing(id, token, userDetails != null ? userDetails.getUser().getId() : null);
        try {
            int n = bookingService.cancelGroupByCustomer(group);
            redirectAttributes.addFlashAttribute("successMessage", userMessages.get("group.cancelled", n));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
        }
        return "redirect:/customer/bookings/group/" + id + (token != null ? "?token=" + token : "");
    }

    private void fillModel(Model model, List<Long> ids, LocalDate checkIn, LocalDate checkOut, CustomUserDetails userDetails) {
        User member = userDetails != null ? customerManagementService.findById(userDetails.getUser().getId()) : null;
        int memberPercent = member != null ? pricingService.memberDiscountPercent(member) : 0;
        Map<Long, Line> lines = new LinkedHashMap<>();
        for (Long id : ids) {
            Room room = roomService.findById(id);
            BigDecimal amount = pricingService.roomAmount(room.getPrice(), checkIn, checkOut);
            BigDecimal promo = promotionService.best(amount, checkIn, checkOut, LocalDate.now())
                    .map(PromotionService.Applied::amount).orElse(BigDecimal.ZERO);
            BigDecimal memberAmount = memberPercent > 0 ? PricingService.memberDiscountAmount(amount.subtract(promo), memberPercent)
                    : BigDecimal.ZERO;
            lines.put(id, new Line(room, amount, promo, memberAmount, amount.subtract(promo).subtract(memberAmount).max(BigDecimal.ZERO)));
        }
        List<Line> list = new ArrayList<>(lines.values());
        // Gia khong hoan tien cho ca doan (uoc tinh tren tong tien phong sau khuyen mai + gia thanh vien)
        int nonRefPercent = pricingService.getSettings().getEffectiveNonRefundablePercent();
        if (nonRefPercent > 0) {
            BigDecimal base = list.stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("nonRefPercent", nonRefPercent);
            model.addAttribute("nonRefAmount", PricingService.memberDiscountAmount(base, nonRefPercent));
        }
        if (member != null) {
            model.addAttribute("memberPercent", memberPercent > 0 ? memberPercent : null);
            model.addAttribute("pointsBalance", member.getPointsBalance());
            model.addAttribute("maxPoints", BookingService.groupMaxPoints(member.getPointsBalance(), list.stream().map(Line::total).toList()));
            model.addAttribute("minRedeemPoints", com.hotel.service.LoyaltyService.MIN_REDEEM_POINTS);
        } else {
            int max = pricingService.getSettings().getMaxMemberDiscount();
            model.addAttribute("memberMaxPercent", max > 0 ? max : null);
        }
        model.addAttribute("lines", list);
        model.addAttribute("roomIds", ids);
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("nights", java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut));
        model.addAttribute("grandTotal", list.stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("checkInOptions", StayTimePolicy.checkInOptions());
        model.addAttribute("checkOutOptions", StayTimePolicy.checkOutOptions());
    }
}
