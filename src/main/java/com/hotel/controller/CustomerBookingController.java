package com.hotel.controller;

import com.hotel.util.Money;
import com.hotel.dto.BookingConfirmRequest;
import com.hotel.entity.Booking;
import com.hotel.entity.CustomerType;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.BookingService;
import com.hotel.service.DivingTourService;
import com.hotel.service.ServiceOrderService;
import com.hotel.service.TransferRateService;
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
import org.springframework.web.util.UriComponentsBuilder;
import com.hotel.service.GuestRegistrationService;
import com.hotel.service.LoyaltyService;
import com.hotel.service.StayTimePolicy;
import com.hotel.service.UserMessages;
import com.hotel.service.VnPayService;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
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
    private final ServiceOrderService serviceOrderService;
    private final DivingTourService divingTourService;
    private final TransferRateService transferRateService;
    private final VnPayService vnPayService;
    private final GuestRegistrationService guestRegistrationService;
    private final com.hotel.service.PromotionService promotionService;
    private final com.hotel.service.RoomServiceService roomServiceService;
    private com.hotel.service.CompanyInvoiceService companyInvoiceService;

    @org.springframework.beans.factory.annotation.Autowired
    void setCompanyInvoiceService(com.hotel.service.CompanyInvoiceService companyInvoiceService) {
        this.companyInvoiceService = companyInvoiceService;
    }

    public CustomerBookingController(BookingService bookingService, RoomService roomService,
                                      ComboService comboService, DiscountCodeService discountCodeService,
                                      CustomerManagementService customerManagementService,
                                      PaymentService paymentService, ReviewService reviewService,
                                      PricingService pricingService, UserMessages userMessages,
                                      ServiceOrderService serviceOrderService, DivingTourService divingTourService,
                                      TransferRateService transferRateService, VnPayService vnPayService,
                                      GuestRegistrationService guestRegistrationService,
                                      com.hotel.service.PromotionService promotionService,
                                      com.hotel.service.RoomServiceService roomServiceService) {
        this.roomServiceService = roomServiceService;
        this.guestRegistrationService = guestRegistrationService;
        this.promotionService = promotionService;
        this.userMessages = userMessages;
        this.vnPayService = vnPayService;
        this.serviceOrderService = serviceOrderService;
        this.divingTourService = divingTourService;
        this.transferRateService = transferRateService;
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
        addPointsModel(model, userDetails);

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
            @RequestParam(defaultValue = "false") boolean wantCompanyInvoice,
            @RequestParam(required = false) String companyName, @RequestParam(required = false) String taxCode,
            @RequestParam(required = false) String companyAddress, @RequestParam(required = false) String invoiceEmail,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        if (bindingResult.hasErrors()) {
            rebuildModel(model, roomId, checkIn, checkOut, guests, comboId);
            addPointsModel(model, userDetails);
            return "customer/booking-confirm";
        }

        try {
            // Neu da dang nhap thi dung dung tai khoan do. Neu chua dang nhap (khach vang lai qua web)
            // thi tu tim/tao tai khoan theo email de gan vao booking, khach khong can biet minh "co tai khoan"
            User customer = (userDetails != null)
                    ? userDetails.getUser()
                    : customerManagementService.findOrCreateGuestCustomer(
                            request.getGuestName(), request.getGuestEmail(), request.getGuestPhone());

            // Hoa don cong ty: kiem tra truoc khi tao don (sai MST thi bao loi, khong tao don)
            com.hotel.service.CompanyInvoiceService.Info companyInfo = wantCompanyInvoice
                    ? com.hotel.service.CompanyInvoiceService.validate(companyName, taxCode, companyAddress, invoiceEmail) : null;
            // Chi khach dang nhap moi dung diem thuong (khach vang lai khong co so du de tru)
            int points = userDetails != null && request.getPointsToUse() != null ? request.getPointsToUse() : 0;
            Booking booking = bookingService.createBooking(
                    customer, roomId, checkIn, checkOut,
                    request.getCheckInTime(), request.getCheckOutTime(),
                    guests, comboId, request.getDiscountCode(),
                    request.getGuestName(), request.getGuestPhone(), request.getGuestEmail(), points,
                    request.getGiftCode()
            );
            if (companyInfo != null) {
                companyInvoiceService.save(booking, companyInfo);
            }
            // Kem ma truy cap de khach vang lai xem lai duoc don (khong can dang nhap)
            return "redirect:/customer/bookings/" + booking.getId() + "?token=" + booking.getAccessToken();
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", userMessages.of(ex));
            rebuildModel(model, roomId, checkIn, checkOut, guests, comboId);
            addPointsModel(model, userDetails);
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
                    Money.format(discountAmount)));
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
                              @RequestParam(required = false) Long tourId,
                              @AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Booking booking = bookingService.findForViewing(id, token,
                userDetails != null ? userDetails.getUser().getId() : null);

        model.addAttribute("booking", booking);
        model.addAttribute("payment", paymentService.findByBookingId(id).orElse(null));
        model.addAttribute("review", reviewService.findByBookingId(id).orElse(null));
        // findForViewing da xac thuc nguoi xem la chu don (dang nhap) hoac co ma truy cap (link trong email moi danh gia)
        model.addAttribute("canReview", reviewService.canReview(booking));
        model.addAttribute("canModify", bookingService.canModify(booking));
        // Da coc: bao truoc tien duoc hoan neu huy ngay bay gio
        model.addAttribute("refundQuote", bookingService.refundQuoteIfCancelledNow(booking));
        if (booking.isAwaitingDeposit()) {
            model.addAttribute("depositQrUrl", paymentService.buildDepositQrUrl(booking));
            model.addAttribute("depositTransferContent", paymentService.depositTransferContent(booking));
            model.addAttribute("bankId", paymentService.getBankId());
            model.addAttribute("accountNo", paymentService.getAccountNo());
            model.addAttribute("accountName", paymentService.getAccountName());
            model.addAttribute("vnpayEnabled", vnPayService.isEnabled());
        }
        model.addAttribute("checkInOptions", withCurrent(StayTimePolicy.checkInOptions(), booking.getCheckInTime()));
        model.addAttribute("checkOutOptions", withCurrent(StayTimePolicy.checkOutOptions(), booking.getCheckOutTime()));
        model.addAttribute("combos", comboService.findActive());
        // Nhan phong som / tra phong muon: phu phi theo % gia 1 dem, le tan duyet
        model.addAttribute("canRequestStayTime", bookingService.canRequestStayTime(booking));
        model.addAttribute("earlyOptions", StayTimePolicy.earlyOptions());
        model.addAttribute("lateOptions", StayTimePolicy.lateOptions());
        model.addAttribute("nightlyRate", StayTimePolicy.nightlyRate(booking));
        // Goi mon len phong khi dang o
        model.addAttribute("canRoomService", roomServiceService.canOrder(booking));
        model.addAttribute("canEditCompanyInvoice", companyInvoiceService.canEdit(booking)
                && booking.getStatus() != com.hotel.entity.BookingStatus.CHECKED_OUT);
        model.addAttribute("roomServiceMenu", roomServiceService.menuByCategory());
        model.addAttribute("roomServiceOrders", roomServiceService.ordersOf(id));
        // Khai bao luu tru online truoc khi den (le tan doi chieu giay to khi nhan phong)
        model.addAttribute("declaredGuests", guestRegistrationService.findByBooking(id));
        model.addAttribute("canDeclareOnline", guestRegistrationService.canDeclareOnline(booking));
        model.addAttribute("guestLimit", GuestRegistrationService.onlineLimit(booking));
        model.addAttribute("genders", com.hotel.entity.Gender.values());
        model.addAttribute("idTypes", com.hotel.entity.IdDocumentType.values());
        // Dich vu them: tour lan + xe dua don san bay (form dat giu lai ?token= cho khach vang lai)
        model.addAttribute("accessToken", token);
        model.addAttribute("tourOrders", serviceOrderService.toursOf(id));
        model.addAttribute("transferOrders", serviceOrderService.transfersOf(id));
        model.addAttribute("canOrderServices", serviceOrderService.canOrderServices(booking));
        model.addAttribute("activeTours", divingTourService.findActive());
        // Den tu nut "Dat tour nay" o trang /services: chon san tour do trong form
        model.addAttribute("preselectTourId", tourId);
        model.addAttribute("transferRates", transferRateService.priceTable());
        model.addAttribute("airports", com.hotel.entity.Airport.values());
        model.addAttribute("vehicles", com.hotel.entity.VehicleType.values());
        model.addAttribute("tourLeadHours", ServiceOrderService.TOUR_LEAD_HOURS);
        model.addAttribute("transferLeadHours", ServiceOrderService.TRANSFER_LEAD_HOURS);
        model.addAttribute("cancelHours", ServiceOrderService.CUSTOMER_CANCEL_HOURS);
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

    // Khach tu khai bao luu tru truoc khi den: chu don (dang nhap) hoac khach vang lai co ?token=
    @PostMapping("/{id}/guests")
    public String declareGuest(@PathVariable Long id, @RequestParam(required = false) String token,
                               @RequestParam(required = false) String fullName,
                               @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                                       iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
                               @RequestParam(required = false) com.hotel.entity.Gender gender,
                               @RequestParam(required = false) com.hotel.entity.IdDocumentType idType,
                               @RequestParam(required = false) String idNumber,
                               @RequestParam(required = false) String nationality,
                               @RequestParam(required = false) String address,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.findForViewing(id, token, userDetails != null ? userDetails.getUser().getId() : null);
        try {
            guestRegistrationService.addGuestOnline(booking, fullName, dateOfBirth, gender, idType, idNumber, nationality, address);
            redirectAttributes.addFlashAttribute("guestSuccess", userMessages.get("precheck.saved"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("guestError", userMessages.of(ex));
        }
        return backTo(id, token, "#khai-bao");
    }

    @PostMapping("/{id}/guests/{guestId}/delete")
    public String removeDeclaredGuest(@PathVariable Long id, @PathVariable Long guestId,
                                      @RequestParam(required = false) String token,
                                      @AuthenticationPrincipal CustomUserDetails userDetails,
                                      RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.findForViewing(id, token, userDetails != null ? userDetails.getUser().getId() : null);
        try {
            guestRegistrationService.removeGuestOnline(booking, guestId);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("guestError", userMessages.of(ex));
        }
        return backTo(id, token, "#khai-bao");
    }

    // Khach tai hoa don PDF cua chinh minh (dang nhap, dung chu don)
    @GetMapping("/{id}/invoice.pdf")
    public Object invoicePdf(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        try {
            Booking booking = bookingService.findByIdForCustomer(id, userDetails.getUser().getId());
            return AdminBookingController.pdfResponse(paymentService.invoicePdf(booking),
                    paymentService.findByBookingId(id).orElseThrow().getInvoiceNo());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
            return "redirect:/customer/bookings";
        }
    }

    // Khach danh gia don da tra phong (1 lan / don)
    @PostMapping("/{id}/review")
    public String review(@PathVariable Long id,
                         @RequestParam(required = false) Integer rating,
                         @RequestParam(required = false) String comment,
                         @RequestParam(value = "media", required = false) List<MultipartFile> media,
                         @RequestParam(required = false) String token,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        try {
            // Khach vang lai danh gia qua link email (?token=) - viet danh gia thay mat chu don
            Booking booking = bookingService.findForViewing(id, token,
                    userDetails != null ? userDetails.getUser().getId() : null);
            reviewService.createReview(id, booking.getCustomer().getId(), rating, comment, media);
            redirectAttributes.addFlashAttribute("reviewSuccess", userMessages.get("msg.reviewThanks"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
        }
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/customer/bookings/{id}");
        if (token != null && !token.isBlank()) {
            url.queryParam("token", token);
        }
        return "redirect:" + url.buildAndExpand(id).encode().toUriString() + "#danh-gia";
    }

    // ===== Khach tu sua / huy don: chu don da dang nhap, HOAC khach vang lai mo bang link co ma truy cap (?token=)
    // (kiem tra quyen bang BookingService.findForViewing giong trang xem don; thao tac nhan danh chu don) =====

    @PostMapping("/{id}/edit-time")
    public String editTime(@PathVariable Long id,
                            @RequestParam LocalTime checkInTime,
                            @RequestParam LocalTime checkOutTime,
                            @RequestParam(required = false) String token,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            RedirectAttributes redirectAttributes) {
        try {
            bookingService.updateTimes(id, ownerId(id, token, userDetails), checkInTime, checkOutTime);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
        }
        return backTo(id, token, "");
    }

    // Khach xin nhan phong som / tra phong muon (de trong = giu nguyen gio do)
    @PostMapping("/{id}/stay-time")
    public String requestStayTime(@PathVariable Long id, @RequestParam(required = false) String token,
                                  @RequestParam(required = false) String checkInTime,
                                  @RequestParam(required = false) String checkOutTime,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.findForViewing(id, token, userDetails != null ? userDetails.getUser().getId() : null);
        try {
            bookingService.requestStayTime(booking, parseTime(checkInTime), parseTime(checkOutTime));
            redirectAttributes.addFlashAttribute("stayTimeSuccess", userMessages.get("stay.sent"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("stayTimeError", userMessages.of(ex));
        }
        return backTo(id, token, "#gio-linh-hoat");
    }

    @PostMapping("/{id}/stay-time/withdraw")
    public String withdrawStayTime(@PathVariable Long id, @RequestParam(required = false) String token,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.findForViewing(id, token, userDetails != null ? userDetails.getUser().getId() : null);
        try {
            bookingService.withdrawStayTime(booking);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("stayTimeError", userMessages.of(ex));
        }
        return backTo(id, token, "#gio-linh-hoat");
    }

    // Khach nhap / bo thong tin xuat hoa don cong ty
    @PostMapping("/{id}/company-invoice")
    public String companyInvoice(@PathVariable Long id, @RequestParam(required = false) String token,
                                 @RequestParam(defaultValue = "save") String action,
                                 @RequestParam(required = false) String companyName, @RequestParam(required = false) String taxCode,
                                 @RequestParam(required = false) String companyAddress, @RequestParam(required = false) String invoiceEmail,
                                 @AuthenticationPrincipal CustomUserDetails userDetails, RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.findForViewing(id, token, userDetails != null ? userDetails.getUser().getId() : null);
        try {
            if (booking.getStatus() == com.hotel.entity.BookingStatus.CHECKED_OUT) {
                throw BusinessException.of("ci.error.locked");
            }
            if ("remove".equals(action)) {
                companyInvoiceService.remove(booking);
            } else {
                companyInvoiceService.save(booking, com.hotel.service.CompanyInvoiceService.validate(companyName, taxCode, companyAddress, invoiceEmail));
            }
            redirectAttributes.addFlashAttribute("ciSuccess", userMessages.get("ci.saved"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("ciError", userMessages.of(ex));
        }
        return backTo(id, token, "#hoa-don-cong-ty");
    }

    // Khach dang o goi mon len phong: so luong gui len o tham so qty_<id mon>
    @PostMapping("/{id}/room-service")
    public String orderRoomService(@PathVariable Long id, @RequestParam(required = false) String token,
                                   @RequestParam(required = false) String note,
                                   @RequestParam Map<String, String> params,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.findForViewing(id, token, userDetails != null ? userDetails.getUser().getId() : null);
        Map<Long, Integer> quantities = new HashMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("qty_") && value != null && !value.isBlank()) {
                try {
                    quantities.put(Long.valueOf(key.substring(4)), Integer.valueOf(value.trim()));
                } catch (NumberFormatException ignored) {
                    // bo qua gia tri la
                }
            }
        });
        try {
            roomServiceService.place(booking, quantities, note);
            redirectAttributes.addFlashAttribute("roomServiceSuccess", userMessages.get("rs.sent"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("roomServiceError", userMessages.of(ex));
        }
        return backTo(id, token, "#goi-phong");
    }

    @PostMapping("/{id}/room-service/{orderId}/cancel")
    public String cancelRoomService(@PathVariable Long id, @PathVariable Long orderId,
                                    @RequestParam(required = false) String token,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.findForViewing(id, token, userDetails != null ? userDetails.getUser().getId() : null);
        try {
            roomServiceService.cancelByGuest(booking, orderId);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("roomServiceError", userMessages.of(ex));
        }
        return backTo(id, token, "#goi-phong");
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    // Xem truoc khi doi ngay: phong con trong khong, tien phong / tong tien moi, chenh lech so voi hien tai
    @GetMapping("/{id}/change-dates/quote")
    @ResponseBody
    public Map<String, Object> quoteChangeDates(@PathVariable Long id,
                                                @RequestParam(required = false) LocalDate checkIn,
                                                @RequestParam(required = false) LocalDate checkOut,
                                                @RequestParam(required = false) String token,
                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> result = new HashMap<>();
        try {
            BookingService.DateChangeQuote q = bookingService.quoteDateChange(id, ownerId(id, token, userDetails), checkIn, checkOut);
            int diffSign = q.difference().signum();
            result.put("ok", true);
            result.put("nights", q.nights());
            result.put("roomAmount", Money.format(q.roomAmount()));
            result.put("total", Money.format(q.totalAmount()));
            result.put("message", userMessages.get(diffSign > 0 ? "changeDates.payMore" : diffSign < 0 ? "changeDates.payLess" : "changeDates.sameTotal",
                    Money.format(q.difference().abs())));
            if (!q.depositPaid() && q.depositAmount().signum() > 0) {
                result.put("depositNote", userMessages.get("changeDates.newDeposit", Money.format(q.depositAmount())));
            }
        } catch (BusinessException ex) {
            result.put("ok", false);
            result.put("message", userMessages.of(ex));
        }
        return result;
    }

    @PostMapping("/{id}/change-dates")
    public String changeDates(@PathVariable Long id,
                              @RequestParam(required = false) LocalDate checkIn,
                              @RequestParam(required = false) LocalDate checkOut,
                              @RequestParam(required = false) String token,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              RedirectAttributes redirectAttributes) {
        try {
            Booking booking = bookingService.changeDates(id, ownerId(id, token, userDetails), checkIn, checkOut);
            redirectAttributes.addFlashAttribute("successMessage", userMessages.get("changeDates.done",
                    booking.getCheckInDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    booking.getCheckOutDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    Money.format(booking.getTotalAmount())));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
        }
        return backTo(id, token, "#doi-ngay");
    }

    @PostMapping("/{id}/edit-combo")
    public String editCombo(@PathVariable Long id,
                             @RequestParam(required = false) Long comboId,
                             @RequestParam(required = false) String token,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        try {
            bookingService.updateCombo(id, ownerId(id, token, userDetails), comboId);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
        }
        return backTo(id, token, "");
    }

    @PostMapping("/{id}/cancel")
    public String cancelBooking(@PathVariable Long id,
                                 @RequestParam(required = false) String token,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        try {
            bookingService.cancelBooking(id, ownerId(id, token, userDetails));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userMessages.of(ex));
        }
        return backTo(id, token, "");
    }

    // Chu don cua booking neu nguoi gui la chu don (dang nhap) hoac co dung ma truy cap - khong thi bao link khong hop le
    private Long ownerId(Long bookingId, String token, CustomUserDetails userDetails) {
        return bookingService.findForViewing(bookingId, token, userDetails != null ? userDetails.getUser().getId() : null)
                .getCustomer().getId();
    }

    // Quay lai trang don, giu ?token= cho khach vang lai
    private static String backTo(Long id, String token, String anchor) {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/customer/bookings/{id}");
        if (token != null && !token.isBlank()) {
            url.queryParam("token", token);
        }
        return "redirect:" + url.buildAndExpand(id).encode().toUriString() + anchor;
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
        model.addAttribute("checkInOptions", StayTimePolicy.checkInOptions());
        model.addAttribute("checkOutOptions", StayTimePolicy.checkOutOptions());
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
        model.addAttribute("refundPolicy", settings);
        // Khuyen mai tu dong ap tren tien phong (truoc ma giam gia / diem thuong)
        var promotion = promotionService.best(roomAmount, checkIn, checkOut, LocalDate.now()).orElse(null);
        model.addAttribute("promotion", promotion);
        if (promotion != null) {
            subtotal = subtotal.subtract(promotion.amount());
        }
        // Uoc tinh truoc khi ap ma giam gia - so chinh xac hien o trang don sau khi dat
        model.addAttribute("estimatedDeposit", pricingService.depositFor(subtotal));
        model.addAttribute("subtotal", subtotal);
    }

    // O "Dung diem thuong" tren trang xac nhan: so du moi nhat va so diem toi da dung duoc cho don nay
    private void addPointsModel(Model model, CustomUserDetails userDetails) {
        if (userDetails == null) {
            return;
        }
        User fresh = customerManagementService.findById(userDetails.getUser().getId());
        BigDecimal subtotal = (BigDecimal) model.getAttribute("subtotal");
        model.addAttribute("pointsBalance", fresh.getPointsBalance());
        model.addAttribute("maxPoints", LoyaltyService.maxRedeemable(fresh, subtotal));
        model.addAttribute("minRedeemPoints", LoyaltyService.MIN_REDEEM_POINTS);
        model.addAttribute("vndPerPoint", LoyaltyService.VND_PER_REDEEMED_POINT);
    }

    // Gio da duoc duyet ngoai khung (nhan som / tra muon) van phai co trong danh sach de form giu dung gia tri
    private static List<LocalTime> withCurrent(List<LocalTime> options, LocalTime current) {
        if (current == null || options.contains(current)) {
            return options;
        }
        List<LocalTime> list = new ArrayList<>(options);
        list.add(current);
        list.sort(null);
        return list;
    }
}
