package com.hotel.controller;

import com.hotel.dto.BookingConfirmRequest;
import com.hotel.entity.Booking;
import com.hotel.entity.CustomerType;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.BookingService;
import com.hotel.service.ComboService;
import com.hotel.service.CustomerManagementService;
import com.hotel.service.DiscountCodeService;
import com.hotel.service.PaymentService;
import com.hotel.service.PricingService;
import com.hotel.service.ReviewService;
import com.hotel.service.RoomService;
import com.hotel.util.PaginationUtil;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.hotel.service.UserMessages;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/customer/bookings")
public class CustomerBookingController {

    // Gio nhan/tra phong mac dinh theo thong le khach san, khach co the doi lai
    private static final LocalTime DEFAULT_CHECK_IN_TIME = LocalTime.of(14, 0);
    private static final LocalTime DEFAULT_CHECK_OUT_TIME = LocalTime.of(12, 0);

    private final BookingService bookingService;
    private final RoomService roomService;
    private final ComboService comboService;
    private final DiscountCodeService discountCodeService;
    private final CustomerManagementService customerManagementService;
    private final PaymentService paymentService;
    private final ReviewService reviewService;
    private final PricingService pricingService;
    private final UserMessages userMessages;

    public CustomerBookingController(BookingService bookingService, RoomService roomService,
                                      ComboService comboService, DiscountCodeService discountCodeService,
                                      CustomerManagementService customerManagementService,
                                      PaymentService paymentService, ReviewService reviewService,
                                      PricingService pricingService, UserMessages userMessages) {
        this.userMessages = userMessages;
        this.bookingService = bookingService;
        this.roomService = roomService;
        this.comboService = comboService;
        this.discountCodeService = discountCodeService;
        this.customerManagementService = customerManagementService;
        this.paymentService = paymentService;
        this.reviewService = reviewService;
        this.pricingService = pricingService;
    }

    // Danh sach lich su dat phong - chi danh cho khach hang DA dang nhap (khach vang lai khong co lich su)
    @GetMapping
    public String history(@AuthenticationPrincipal CustomUserDetails userDetails,
                           @RequestParam(defaultValue = "1") int page, Model model) {
        List<Booking> all = bookingService.findAllForCustomer(userDetails.getUser().getId());
        int totalPages = PaginationUtil.totalPages(all.size(), PaginationUtil.DEFAULT_PAGE_SIZE);
        List<Booking> pageBookings = PaginationUtil.slice(all, page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("bookings", pageBookings);
        model.addAttribute("payments", paymentService.findByBookings(pageBookings));
        model.addAttribute("reviews", reviewService.findByBookings(pageBookings));
        model.addAttribute("currentPage", Math.max(1, Math.min(page, totalPages)));
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(page, totalPages));
        return "customer/booking-history";
    }

    // Trang xac nhan dat phong - KHONG bat buoc dang nhap, khach nhap ho ten/email/SDT truc tiep
    @GetMapping("/new")
    public String confirmForm(
            @RequestParam Long roomId,
            @RequestParam LocalDate checkIn,
            @RequestParam LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) Long comboId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        rebuildModel(model, roomId, checkIn, checkOut, guests, comboId);

        BookingConfirmRequest request = new BookingConfirmRequest();
        request.setCheckInTime(DEFAULT_CHECK_IN_TIME);
        request.setCheckOutTime(DEFAULT_CHECK_OUT_TIME);
        // Ngay doi khach: mac dinh nhan phong khong som hon gio khach truoc tra, tra phong khong muon hon gio khach sau nhan
        BookingService.Availability availability = (BookingService.Availability) model.getAttribute("availability");
        if (availability != null) {
            if (availability.earliestCheckIn() != null && DEFAULT_CHECK_IN_TIME.isBefore(availability.earliestCheckIn())) {
                request.setCheckInTime(availability.earliestCheckIn());
            }
            if (availability.latestCheckOut() != null && DEFAULT_CHECK_OUT_TIME.isAfter(availability.latestCheckOut())) {
                request.setCheckOutTime(availability.latestCheckOut());
            }
        }
        // Neu da dang nhap thi dien san thong tin tu tai khoan, khach van sua duoc.
        // Neu chua dang nhap (khach vang lai) thi de trong, khach tu nhap.
        if (userDetails != null) {
            request.setGuestName(userDetails.getUser().getFullName());
            request.setGuestPhone(userDetails.getUser().getPhoneNumber());
            request.setGuestEmail(userDetails.getUser().getEmail());
        }
        model.addAttribute("bookingConfirmRequest", request);

        return "customer/booking-confirm";
    }

    @PostMapping("/new")
    public String createBooking(
            @RequestParam Long roomId,
            @RequestParam LocalDate checkIn,
            @RequestParam LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) Long comboId,
            @Valid @ModelAttribute("bookingConfirmRequest") BookingConfirmRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        if (bindingResult.hasErrors()) {
            rebuildModel(model, roomId, checkIn, checkOut, guests, comboId);
            return "customer/booking-confirm";
        }

        try {
            // Neu da dang nhap thi dung dung tai khoan do. Neu chua dang nhap (khach vang lai qua web)
            // thi tu tim/tao tai khoan theo email de gan vao booking, khach khong can biet minh "co tai khoan"
            User customer = (userDetails != null)
                    ? userDetails.getUser()
                    : customerManagementService.findOrCreateGuestCustomer(
                            request.getGuestName(), request.getGuestEmail(), request.getGuestPhone());

            Booking booking = bookingService.createBooking(
                    customer, roomId, checkIn, checkOut,
                    request.getCheckInTime(), request.getCheckOutTime(),
                    guests, comboId, request.getDiscountCode(),
                    request.getGuestName(), request.getGuestPhone(), request.getGuestEmail()
            );
            // Kem ma truy cap de khach vang lai xem lai duoc don (khong can dang nhap)
            return "redirect:/customer/bookings/" + booking.getId() + "?token=" + booking.getAccessToken();
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", userMessages.of(ex));
            rebuildModel(model, roomId, checkIn, checkOut, guests, comboId);
            return "customer/booking-confirm";
        }
    }

    // Kiem tra ma giam gia co hop le khong, tra ve JSON, khong tao booking, khong reload trang
    // Neu chua dang nhap thi mac dinh coi la khach lan dau (NEW) de xem truoc
    @GetMapping("/check-discount")
    @ResponseBody
    public Map<String, Object> checkDiscountCode(
            @RequestParam String code,
            @RequestParam Long roomId,
            @RequestParam LocalDate checkIn,
            @RequestParam LocalDate checkOut,
            @RequestParam(required = false) Long comboId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Map<String, Object> result = new HashMap<>();
        try {
            var room = roomService.findById(roomId);
            BigDecimal subtotal = pricingService.roomAmount(room.getPrice(), checkIn, checkOut);
            if (comboId != null) {
                subtotal = subtotal.add(comboService.findById(comboId).getPrice());
            }

            CustomerType customerType = (userDetails != null)
                    ? userDetails.getUser().getCustomerType()
                    : CustomerType.NEW;

            BigDecimal discountAmount = discountCodeService.validateAndCalculateDiscount(code, customerType, subtotal);
            // Khach vang lai chua biet la ai (chua nhap email) - se duoc kiem tra lai khi bam Xac nhan dat phong
            if (userDetails != null && bookingService.hasUsedDiscountCode(userDetails.getUser().getId(), code)) {
                throw BusinessException.of("err.discountUsed");
            }

            result.put("valid", true);
            result.put("discountAmount", discountAmount);
            result.put("message", userMessages.get("msg.discountValid",
                    NumberFormat.getIntegerInstance(LocaleContextHolder.getLocale()).format(discountAmount)));
        } catch (BusinessException ex) {
            result.put("valid", false);
            result.put("message", userMessages.of(ex));
        }
        return result;
    }

    // Trang xem chi tiet 1 booking (dung lam trang "dat phong thanh cong") - KHONG bat buoc dang nhap.
    // Khach vang lai phai co dung ma truy cap (?token=... trong link sau khi dat / trong email);
    // khach da dang nhap thi xem duoc don cua chinh minh. Khong cho xem don nguoi khac chi bang cach doi id.
    @GetMapping("/{id}")
    public String viewBooking(@PathVariable Long id, @RequestParam(required = false) String token,
                              @AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Booking booking = bookingService.findForViewing(id, token,
                userDetails != null ? userDetails.getUser().getId() : null);

        model.addAttribute("booking", booking);
        model.addAttribute("payment", paymentService.findByBookingId(id).orElse(null));
        model.addAttribute("review", reviewService.findByBookingId(id).orElse(null));
        model.addAttribute("canReview", userDetails != null && reviewService.canReview(booking, userDetails.getUser().getId()));
        model.addAttribute("canModify", bookingService.canModify(booking));
        if (booking.isAwaitingDeposit()) {
            model.addAttribute("depositQrUrl", paymentService.buildDepositQrUrl(booking));
            model.addAttribute("depositTransferContent", paymentService.depositTransferContent(booking));
            model.addAttribute("bankId", paymentService.getBankId());
            model.addAttribute("accountNo", paymentService.getAccountNo());
            model.addAttribute("accountName", paymentService.getAccountName());
        }
        model.addAttribute("timeOptions", generateTimeOptions());
        model.addAttribute("combos", comboService.findActive());
        return "customer/booking-success";
    }

    // Khach xem/in hoa don cua chinh minh - bat buoc dang nhap (thuoc /customer/**) va dung chu don
    @GetMapping("/{id}/invoice")
    public String invoice(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
                          Model model, RedirectAttributes redirectAttributes) {
        try {
            Booking booking = bookingService.findByIdForCustomer(id, userDetails.getUser().getId());
            model.addAllAttributes(paymentService.buildInvoiceData(booking));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
            return "redirect:/customer/bookings";
        }
        model.addAttribute("backUrl", "/customer/bookings/" + id);
        return "admin/bookings/invoice";
    }

    // Khach danh gia don da tra phong (1 lan / don)
    @PostMapping("/{id}/review")
    public String review(@PathVariable Long id,
                         @RequestParam(required = false) Integer rating,
                         @RequestParam(required = false) String comment,
                         @RequestParam(value = "media", required = false) List<MultipartFile> media,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        try {
            reviewService.createReview(id, userDetails.getUser().getId(), rating, comment, media);
            redirectAttributes.addFlashAttribute("reviewSuccess", userMessages.get("msg.reviewThanks"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
        }
        return "redirect:/customer/bookings/" + id + "#danh-gia";
    }

    @PostMapping("/{id}/edit-time")
    public String editTime(@PathVariable Long id,
                            @RequestParam LocalTime checkInTime,
                            @RequestParam LocalTime checkOutTime,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            RedirectAttributes redirectAttributes) {
        try {
            bookingService.updateTimes(id, userDetails.getUser().getId(), checkInTime, checkOutTime);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/{id}/edit-combo")
    public String editCombo(@PathVariable Long id,
                             @RequestParam(required = false) Long comboId,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        try {
            bookingService.updateCombo(id, userDetails.getUser().getId(), comboId);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancelBooking(@PathVariable Long id,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        try {
            bookingService.cancelBooking(id, userDetails.getUser().getId());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
        }
        return "redirect:/customer/bookings/" + id;
    }

    // Thong tin tom tat don: gia tung dem (ngay thuong / cuoi tuan / le), tien phong, combo, tien coc du kien
    private void rebuildModel(Model model, Long roomId, LocalDate checkIn, LocalDate checkOut, Integer guests, Long comboId) {
        var room = roomService.findById(roomId);
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        var nightlyPrices = pricingService.nightlyPrices(room.getPrice(), checkIn, checkOut);
        BigDecimal roomAmount = nightlyPrices.stream().map(PricingService.NightPrice::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subtotal = roomAmount;

        model.addAttribute("room", room);
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("guests", guests);
        model.addAttribute("nights", nights);
        model.addAttribute("nightlyPrices", nightlyPrices);
        model.addAttribute("roomAmount", roomAmount);
        model.addAttribute("comboId", comboId);
        model.addAttribute("timeOptions", generateTimeOptions());
        if (comboId != null) {
            var combo = comboService.findById(comboId);
            model.addAttribute("combo", combo);
            subtotal = subtotal.add(combo.getPrice());
        }
        // Bao truoc neu phong khong con trong / gio nhan-tra bi gioi han do co khach truoc-sau trong ngay
        if (nights > 0) {
            BookingService.Availability availability = bookingService.checkAvailability(room, checkIn, checkOut);
            model.addAttribute("availability", availability);
            model.addAttribute("availabilityMessage", availability.describe(userMessages::get));
        }
        var settings = pricingService.getSettings();
        model.addAttribute("depositPercent", settings.getDepositPercent());
        model.addAttribute("depositDeadlineHours", settings.getDepositDeadlineHours());
        // Uoc tinh truoc khi ap ma giam gia - so chinh xac hien o trang don sau khi dat
        model.addAttribute("estimatedDeposit", pricingService.depositFor(subtotal));
    }

    // Danh sach gio de chon (moi 30 phut) - thay cho o nhap gio mac dinh cua trinh duyet, de chon hon
    private static List<LocalTime> generateTimeOptions() {
        List<LocalTime> options = new ArrayList<>();
        LocalTime t = LocalTime.MIDNIGHT;
        for (int i = 0; i < 48; i++) {
            options.add(t);
            t = t.plusMinutes(30);
        }
        return options;
    }
}
