package com.hotel.controller;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.GiftVoucher;
import com.hotel.entity.Payment;
import com.hotel.entity.PaymentStatus;
import com.hotel.entity.PointTransaction;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.BookingService;
import com.hotel.service.GiftVoucherService;
import com.hotel.service.LoyaltyService;
import com.hotel.service.PaymentService;
import com.hotel.service.UserMessages;
import com.hotel.service.UserService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Khach tu phuc vu (role CUSTOMER): lich su diem thuong, hoa don da thanh toan, the qua tang da mua / duoc tang
@Controller
@RequestMapping("/customer/account")
public class CustomerSelfServiceController {

    private static final Set<BookingStatus> UPCOMING = EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

    private final UserService userService;
    private final BookingService bookingService;
    private final LoyaltyService loyaltyService;
    private final PaymentService paymentService;
    private final GiftVoucherService giftVoucherService;
    private final com.hotel.repository.BookingRepository bookingRepository;
    private final UserMessages userMessages;

    public CustomerSelfServiceController(UserService userService, BookingService bookingService, LoyaltyService loyaltyService,
                                         PaymentService paymentService, GiftVoucherService giftVoucherService,
                                         com.hotel.repository.BookingRepository bookingRepository, UserMessages userMessages) {
        this.userService = userService;
        this.bookingService = bookingService;
        this.loyaltyService = loyaltyService;
        this.paymentService = paymentService;
        this.giftVoucherService = giftVoucherService;
        this.bookingRepository = bookingRepository;
        this.userMessages = userMessages;
    }

    @GetMapping("/points")
    public String points(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestParam(defaultValue = "1") int page, Model model) {
        User user = userService.findById(userDetails.getUser().getId());
        Pager<PointTransaction> pager = Pager.of(loyaltyService.fullHistory(user.getId()), page, PaginationUtil.DEFAULT_PAGE_SIZE * 2);
        // Diem du kien: cac ky nghi chua tra phong, tinh theo hang hien tai (so that cong khi tra phong)
        List<Booking> upcoming = bookingService.findAllForCustomer(user.getId()).stream()
                .filter(b -> UPCOMING.contains(b.getStatus()) && b.getPointsEarned() == null).toList();
        int expected = upcoming.stream().mapToInt(b -> LoyaltyService.pointsFor(b.getTotalAmount(), user.getCustomerType())).sum();
        model.addAttribute("account", user);
        model.addAttribute("pager", pager);
        model.addAttribute("transactions", pager.items());
        model.addAttribute("expectedPoints", expected);
        model.addAttribute("upcomingStays", upcoming.size());
        model.addAttribute("earnMultiplier", LoyaltyService.earnMultiplier(user.getCustomerType()));
        return "customer/self/points";
    }

    // 1 dong hoa don: don + phieu thanh toan
    public record InvoiceLine(Booking booking, Payment payment) {
    }

    @GetMapping("/invoices")
    public String invoices(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestParam(defaultValue = "1") int page, Model model) {
        List<Booking> bookings = bookingService.findAllForCustomer(userDetails.getUser().getId());
        Map<Long, Payment> payments = paymentService.findByBookings(bookings);
        List<InvoiceLine> lines = bookings.stream()
                .map(b -> new InvoiceLine(b, payments.get(b.getId())))
                .filter(l -> l.payment() != null && l.payment().getStatus() == PaymentStatus.PAID && l.payment().getPaymentDate() != null)
                .sorted(Comparator.comparing((InvoiceLine l) -> l.payment().getPaymentDate()).reversed())
                .toList();
        Pager<InvoiceLine> pager = Pager.of(lines, page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("pager", pager);
        model.addAttribute("lines", pager.items());
        return "customer/self/invoices";
    }

    @GetMapping("/gift-vouchers")
    public String giftVouchers(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        String email = userDetails.getUser().getEmail();
        List<GiftVoucher> vouchers = giftVoucherService.forEmail(email);
        Map<Long, List<Booking>> usages = new LinkedHashMap<>();
        for (GiftVoucher v : vouchers) {
            usages.put(v.getId(), bookingRepository.findByGiftVoucherCodeIgnoreCaseOrderByCreatedAtDesc(v.getCode()).stream()
                    .filter(b -> b.getGiftVoucherAmountOrZero().signum() > 0).toList());
        }
        Map<Long, Integer> percents = new LinkedHashMap<>();
        for (GiftVoucher v : vouchers) {
            percents.put(v.getId(), v.getAmount().signum() > 0
                    ? v.getBalance().multiply(java.math.BigDecimal.valueOf(100)).divide(v.getAmount(), 0, java.math.RoundingMode.DOWN).intValue() : 0);
        }
        model.addAttribute("percents", percents);
        model.addAttribute("vouchers", vouchers);
        model.addAttribute("usages", usages);
        model.addAttribute("myEmail", email);
        model.addAttribute("now", java.time.LocalDateTime.now());
        return "customer/self/gift-vouchers";
    }

    @PostMapping("/gift-vouchers/{id}/resend")
    public String resend(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        try {
            giftVoucherService.resendToRecipient(id, userDetails.getUser().getEmail());
            GiftVoucher v = giftVoucherService.forEmail(userDetails.getUser().getEmail()).stream()
                    .filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
            redirectAttributes.addFlashAttribute("successMessage", userMessages.get("self.gv.resent", v.getRecipientEmail()));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
        }
        return "redirect:/customer/account/gift-vouchers";
    }
}
