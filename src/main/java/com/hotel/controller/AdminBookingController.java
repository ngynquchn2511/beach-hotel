package com.hotel.controller;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.ChargeType;
import com.hotel.entity.Gender;
import com.hotel.entity.IdDocumentType;
import com.hotel.entity.PaymentMethod;
import com.hotel.exception.BusinessException;
import com.hotel.service.BookingService;
import com.hotel.service.DivingTourService;
import com.hotel.service.ServiceOrderService;
import com.hotel.service.GuestRegistrationService;
import com.hotel.service.PaymentService;
import org.springframework.format.annotation.DateTimeFormat;
import com.hotel.util.PaginationUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
@RequestMapping("/admin/bookings")
public class AdminBookingController {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final GuestRegistrationService guestRegistrationService;
    private final ServiceOrderService serviceOrderService;
    private final DivingTourService divingTourService;
    private final com.hotel.service.VnPayService vnPayService;
    private com.hotel.service.CompanyInvoiceService companyInvoiceService;

    @org.springframework.beans.factory.annotation.Autowired
    void setCompanyInvoiceService(com.hotel.service.CompanyInvoiceService companyInvoiceService) {
        this.companyInvoiceService = companyInvoiceService;
    }

    public AdminBookingController(BookingService bookingService, PaymentService paymentService,
                                  GuestRegistrationService guestRegistrationService,
                                  ServiceOrderService serviceOrderService, DivingTourService divingTourService,
                                  com.hotel.service.VnPayService vnPayService) {
        this.vnPayService = vnPayService;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.guestRegistrationService = guestRegistrationService;
        this.serviceOrderService = serviceOrderService;
        this.divingTourService = divingTourService;
    }

    // Danh sach toan bo booking - dung lam "lich su dat phong" va man hinh check-in/check-out cho STAFF/ADMIN
    // Co the loc theo trang thai qua query param ?status=PENDING, va tim theo ten/email/sdt qua ?q=
    @GetMapping
    public String list(@RequestParam(required = false) BookingStatus status,
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) String refund,
                        @RequestParam(defaultValue = "1") int page, Model model) {
        List<Booking> all = bookingService.findAllBookings(status, q);
        // ?refund=pending: chi cac don da huy dang cho hoan coc (link tu dashboard / sidebar)
        boolean refundPending = "pending".equals(refund);
        if (refundPending) {
            all = all.stream().filter(Booking::isRefundPending).toList();
        }
        model.addAttribute("refundPending", refundPending);
        int totalPages = PaginationUtil.totalPages(all.size(), PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("bookings", PaginationUtil.slice(all, page, PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("currentPage", Math.max(1, Math.min(page, totalPages)));
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(page, totalPages));
        model.addAttribute("statusFilter", status);
        model.addAttribute("searchQuery", q);
        model.addAttribute("allStatuses", BookingStatus.values());
        return "admin/bookings/list";
    }

    // Xem chi tiet 1 don - cung noi thao tac xac nhan/check-in/check-out/huy
    // Mo xem chi tiet se tat cac canh bao (don moi / doi gio nhan phong) cua don nay
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Booking booking = bookingService.findByIdAndMarkSeenByStaff(id);
        model.addAttribute("booking", booking);
        model.addAttribute("payment", paymentService.findByBookingId(id).orElse(null));
        model.addAttribute("guests", guestRegistrationService.findByBooking(id));
        model.addAttribute("canEditCompanyInvoice", companyInvoiceService.canEdit(bookingService.findById(id)));
        model.addAttribute("canEditGuests", guestRegistrationService.canEdit(booking));
        model.addAttribute("genders", Gender.values());
        model.addAttribute("idTypes", IdDocumentType.values());
        if (booking.isAwaitingDeposit()) {
            model.addAttribute("depositTransferContent", paymentService.depositTransferContent(booking));
        }
        // Tien hoan coc du kien neu huy ngay bay gio (null = chua coc / khong huy duoc)
        model.addAttribute("refundQuote", bookingService.refundQuoteIfCancelledNow(booking));
        model.addAttribute("canMarkNoShow", bookingService.canMarkNoShow(booking));
        model.addAttribute("upgradeOffers", upgradeService.offersOf(id));
        model.addAttribute("requestTagOptions", com.hotel.entity.GuestRequestTag.values());
        model.addAttribute("messages", messageService.thread(id));
        model.addAttribute("quickReplies", quickReplyService.forBooking(booking));
        if (callReminderService != null) {
            model.addAttribute("calls", callReminderService.forBooking(id));
            model.addAttribute("callPurposes", com.hotel.entity.CallReminder.Purpose.values());
            model.addAttribute("callResults", com.hotel.entity.CallReminder.Result.values());
        }
        messageService.markReadByStaff(id);
        model.addAttribute("upgradeCandidates", upgradeService.candidates(booking));
        // So lan khach nay khong den o cac don KHAC (canh bao nen yeu cau coc)
        long noShows = booking.getCustomer() != null ? bookingService.noShowCount(booking.getCustomer().getId()) : 0;
        model.addAttribute("otherNoShows", noShows - (booking.isNoShow() ? 1 : 0));
        // Dich vu them (tour lan / xe dua don) cua don + form le tan dat ho khach
        model.addAttribute("tourOrders", serviceOrderService.toursOf(id));
        model.addAttribute("transferOrders", serviceOrderService.transfersOf(id));
        model.addAttribute("canOrderServices", serviceOrderService.canOrderServices(booking));
        model.addAttribute("activeTours", divingTourService.findActive());
        model.addAttribute("airports", com.hotel.entity.Airport.values());
        model.addAttribute("vehicles", com.hotel.entity.VehicleType.values());
        model.addAttribute("directions", com.hotel.entity.TransferDirection.values());
        return "admin/bookings/detail";
    }

    // Kiem tra app ngan hang thay tien coc da vao -> ghi nhan da coc + xac nhan don
    @PostMapping("/{id}/confirm-deposit")
    public String confirmDeposit(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bookingService.confirmDeposit(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ghi nhận tiền cọc và xác nhận đơn");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
    }

    // Khai bao luu tru: them 1 khach (giay to tuy than) vao don
    @PostMapping("/{id}/guests")
    public String addGuest(@PathVariable Long id,
                           @RequestParam(required = false) String fullName,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
                           @RequestParam(required = false) Gender gender,
                           @RequestParam(required = false) IdDocumentType idType,
                           @RequestParam(required = false) String idNumber,
                           @RequestParam(required = false) String nationality,
                           @RequestParam(required = false) String address,
                           RedirectAttributes redirectAttributes) {
        try {
            guestRegistrationService.addGuest(id, fullName, dateOfBirth, gender, idType, idNumber, nationality, address);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm khách lưu trú");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id + "#khach-luu-tru";
    }

    @PostMapping("/{id}/guests/{guestId}/delete")
    public String removeGuest(@PathVariable Long id, @PathVariable Long guestId, RedirectAttributes redirectAttributes) {
        try {
            guestRegistrationService.removeGuest(id, guestId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa khách lưu trú");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id + "#khach-luu-tru";
    }

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bookingService.confirmBooking(id);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
    }

    @PostMapping("/{id}/check-in")
    // back=today: bam tu man hinh "Hom nay" -> quay lai do (chi nhan dung gia tri nay, khong redirect tuy y)
    public String checkIn(@PathVariable Long id, @RequestParam(required = false) String back,
                          RedirectAttributes redirectAttributes) {
        try {
            var booking = bookingService.checkIn(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã check-in đơn #" + id + " - phòng " + booking.getRoom().getRoomNumber());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "today".equals(back) ? "redirect:/admin/today" : "redirect:/admin/bookings/" + id;
    }

    // Man hinh check-out: chon phuong thuc thanh toan (tien mat / chuyen khoan QR / the) truoc khi tra phong
    @GetMapping("/{id}/check-out")
    public String checkOutForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.findById(id);
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ có thể check-out đơn đã check-in (CHECKED_IN)");
            return "redirect:/admin/bookings/" + id;
        }
        model.addAttribute("booking", booking);
        model.addAttribute("nights", ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate()));
        model.addAttribute("paid", paymentService.isPaid(id));
        model.addAttribute("qrUrl", paymentService.buildQrUrl(booking));
        model.addAttribute("transferContent", paymentService.transferContent(booking));
        model.addAttribute("bankId", paymentService.getBankId());
        model.addAttribute("accountNo", paymentService.getAccountNo());
        model.addAttribute("accountName", paymentService.getAccountName());
        model.addAttribute("charges", paymentService.findCharges(id));
        model.addAttribute("chargesTotal", paymentService.chargesTotal(id));
        model.addAttribute("grandTotal", paymentService.grandTotal(booking));
        model.addAttribute("depositPaid", booking.getDepositPaidAmount());
        model.addAttribute("amountDue", paymentService.amountDue(booking));
        model.addAttribute("chargeTypes", ChargeType.manualTypes());
        return "admin/bookings/checkout";
    }

    // Them phu phi phat sinh truoc khi thu tien (minibar, giat ui, hu hong, tra phong muon...)
    @PostMapping("/{id}/charges")
    public String addCharge(@PathVariable Long id,
                            @RequestParam(required = false) ChargeType type,
                            @RequestParam(required = false) String description,
                            @RequestParam(required = false) BigDecimal amount,
                            RedirectAttributes redirectAttributes) {
        try {
            paymentService.addCharge(id, type, description, amount);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm phụ phí");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id + "/check-out#phu-phi";
    }

    @PostMapping("/{id}/charges/{chargeId}/delete")
    public String removeCharge(@PathVariable Long id, @PathVariable Long chargeId, RedirectAttributes redirectAttributes) {
        try {
            paymentService.removeCharge(id, chargeId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa phụ phí");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id + "/check-out#phu-phi";
    }

    // Khong gui phuong thuc thanh toan -> mac dinh tien mat (thu tai quay)
    @PostMapping("/{id}/check-out")
    public String checkOut(@PathVariable Long id,
                           @RequestParam(defaultValue = "CASH") PaymentMethod paymentMethod,
                           RedirectAttributes redirectAttributes) {
        try {
            paymentService.checkOutWithPayment(id, paymentMethod);
            redirectAttributes.addFlashAttribute("successMessage", "Check-out thành công. Bạn có thể xuất hóa đơn cho khách.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            // Don van dang o (chua tra phong) -> quay lai man hinh thanh toan de chon lai phuong thuc
            if (bookingService.findById(id).getStatus() == BookingStatus.CHECKED_IN) {
                return "redirect:/admin/bookings/" + id + "/check-out";
            }
        }
        return "redirect:/admin/bookings/" + id;
    }

    // Nhan vien kiem tra app ngan hang thay tien da vao -> bam xac nhan, QR chuyen sang "thanh cong"
    @PostMapping("/{id}/confirm-transfer")
    public String confirmTransfer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            paymentService.confirmBankTransfer(id);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id + "/check-out?method=BANK_TRANSFER";
    }

    // Hoa don thanh toan - trang in rieng, bam "In / Lưu PDF" de xuat file
    @GetMapping("/{id}/invoice")
    public String invoice(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAllAttributes(paymentService.buildInvoiceData(bookingService.findById(id)));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/bookings/" + id;
        }
        model.addAttribute("backUrl", "/admin/bookings/" + id);
        return "admin/bookings/invoice";
    }

    // Le tan nhap / sua / bo thong tin hoa don cong ty (truoc khi ke toan xuat HDDT)
    @PostMapping("/{id}/company-invoice")
    public String companyInvoice(@PathVariable Long id, @RequestParam(defaultValue = "save") String action,
                                 @RequestParam(required = false) String companyName, @RequestParam(required = false) String taxCode,
                                 @RequestParam(required = false) String companyAddress, @RequestParam(required = false) String invoiceEmail,
                                 RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.findById(id);
        try {
            if ("remove".equals(action)) {
                companyInvoiceService.remove(booking);
            } else {
                companyInvoiceService.save(booking, com.hotel.service.CompanyInvoiceService.validate(companyName, taxCode, companyAddress, invoiceEmail));
            }
            redirectAttributes.addFlashAttribute("ciSuccess", "Đã lưu thông tin hóa đơn công ty");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("ciError", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id + "#hoa-don-cong-ty";
    }

    // Doi phong: chon phong con trong cac dem cua don (kem chenh lech gia)
    @GetMapping("/{id}/move")
    public String moveForm(@PathVariable Long id, Model model) {
        Booking booking = bookingService.findById(id);
        if (!bookingService.canMoveRoom(booking)) {
            return "redirect:/admin/bookings/" + id;
        }
        model.addAttribute("booking", booking);
        model.addAttribute("options", bookingService.roomMoveOptions(id));
        return "admin/bookings/move";
    }

    @PostMapping("/{id}/move")
    public String move(@PathVariable Long id, @RequestParam(required = false) Long newRoomId,
                       @RequestParam(defaultValue = "false") boolean waiveDifference,
                       @RequestParam(required = false) String note, RedirectAttributes redirectAttributes) {
        try {
            Booking moved = bookingService.moveRoom(id, newRoomId, waiveDifference, note);
            redirectAttributes.addFlashAttribute("successMessage", "Đã chuyển sang phòng " + moved.getRoom().getRoomNumber()
                    + " và gửi email báo khách");
            return "redirect:/admin/bookings/" + id;
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/bookings/" + id + "/move";
        }
    }

    // Hoan coc qua VNPay (coc da tra qua VNPay): goi API hoan tien, thanh cong thi tu ghi nhan + email khach
    @PostMapping("/{id}/refund-vnpay")
    public String refundViaVnpay(@PathVariable Long id, @org.springframework.security.core.annotation.AuthenticationPrincipal com.hotel.security.CustomUserDetails user,
                                 jakarta.servlet.http.HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            vnPayService.refundDeposit(id, user != null ? user.getUser().getFullName() : null, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("successMessage", "VNPay đã hoàn tiền cho khách, đã gửi email báo khách");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
    }

    // Duyet / tu choi yeu cau nhan phong som / tra phong muon (duyet -> doi gio + cong phu phi vao don)
    @PostMapping("/{id}/stay-time/approve")
    public String approveStayTime(@PathVariable Long id, @RequestParam(required = false) String back,
                                  RedirectAttributes redirectAttributes) {
        try {
            bookingService.approveStayTime(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã duyệt, phụ phí đã cộng vào đơn và gửi email báo khách");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "today".equals(back) ? "redirect:/admin/today" : "redirect:/admin/bookings/" + id;
    }

    @PostMapping("/{id}/stay-time/reject")
    public String rejectStayTime(@PathVariable Long id, @RequestParam(required = false) String note,
                                 @RequestParam(required = false) String back, RedirectAttributes redirectAttributes) {
        try {
            bookingService.rejectStayTime(id, note);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối yêu cầu và báo khách");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "today".equals(back) ? "redirect:/admin/today" : "redirect:/admin/bookings/" + id;
    }

    // Tai hoa don PDF (in / gui khach qua Zalo...)
    @GetMapping("/{id}/invoice.pdf")
    public Object invoicePdf(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Booking booking = bookingService.findById(id);
            return pdfResponse(paymentService.invoicePdf(booking), paymentService.findByBookingId(id).orElseThrow().getInvoiceNo());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/bookings/" + id;
        }
    }

    static org.springframework.http.ResponseEntity<byte[]> pdfResponse(byte[] pdf, String invoiceNo) {
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.inline()
                                .filename(com.hotel.service.InvoicePdfService.fileName(invoiceNo)).build().toString())
                .body(pdf);
    }

    // Gui lai hoa don qua email cho khach (VD khach bao chua nhan duoc)
    @PostMapping("/{id}/invoice/email")
    public String emailInvoice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            String email = paymentService.resendInvoice(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi hóa đơn tới " + email);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
    }

    // Le tan tra loi tin nhan cua khach
    @PostMapping("/{id}/messages")
    public String replyMessage(@PathVariable Long id, @RequestParam(required = false) String body,
                               @org.springframework.security.core.annotation.AuthenticationPrincipal com.hotel.security.CustomUserDetails user,
                               RedirectAttributes redirectAttributes) {
        try {
            messageService.staffReply(bookingService.findById(id), body, user != null ? user.getUser().getFullName() : null);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi trả lời - khách nhận email kèm link đơn");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id + "#tin-nhan";
    }

    private com.hotel.service.BookingMessageService messageService;
    private com.hotel.service.QuickReplyService quickReplyService;
    private com.hotel.service.CallReminderService callReminderService;

    @org.springframework.beans.factory.annotation.Autowired
    void setCallReminderService(com.hotel.service.CallReminderService callReminderService) {
        this.callReminderService = callReminderService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setQuickReplyService(com.hotel.service.QuickReplyService quickReplyService) {
        this.quickReplyService = quickReplyService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setMessageService(com.hotel.service.BookingMessageService messageService) {
        this.messageService = messageService;
    }

    // Le tan sua yeu cau dac biet cua don
    @PostMapping("/{id}/requests")
    public String updateRequests(@PathVariable Long id, @RequestParam(required = false) java.util.List<com.hotel.entity.GuestRequestTag> requestTags,
                                 @RequestParam(required = false) String specialRequests, RedirectAttributes redirectAttributes) {
        try {
            bookingService.updateRequests(bookingService.findById(id), requestTags, specialRequests, false);
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu yêu cầu đặc biệt");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id + "#yeu-cau";
    }

    // Ghi chu so thich / luu y ve khach (luu vao ho so, hien o cac don sau)
    @PostMapping("/{id}/guest-notes")
    public String updateGuestNotes(@PathVariable Long id, @RequestParam(required = false) String staffNotes, RedirectAttributes redirectAttributes) {
        try {
            Booking booking = bookingService.findById(id);
            if (booking.getCustomer() == null) {
                throw new BusinessException("Đơn không gắn với tài khoản khách");
            }
            bookingService.updateStaffNotes(booking.getCustomer().getId(), staffNotes);
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu ghi chú về khách - hiện lại ở các đơn sau");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id + "#yeu-cau";
    }

    // Gui loi moi nang hang phong cho khach (email + hien tren trang don)
    @PostMapping("/{id}/upgrade-offers")
    public String sendUpgradeOffers(@PathVariable Long id, @org.springframework.security.core.annotation.AuthenticationPrincipal com.hotel.security.CustomUserDetails user,
                                    RedirectAttributes redirectAttributes) {
        try {
            var offers = upgradeService.createOffers(id, user != null ? user.getUser().getFullName() : null);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi " + offers.size() + " lời mời nâng hạng cho khách");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id + "#nang-hang";
    }

    private com.hotel.service.UpgradeService upgradeService;

    @org.springframework.beans.factory.annotation.Autowired
    void setUpgradeService(com.hotel.service.UpgradeService upgradeService) {
        this.upgradeService = upgradeService;
    }

    // Khach khong den: dong don, giu coc. back=today -> quay lai man hinh "Hom nay"
    @PostMapping("/{id}/no-show")
    public String noShow(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes redirectAttributes) {
        try {
            Booking booking = bookingService.markNoShow(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã đánh dấu khách không đến đơn #" + id + ", phòng "
                    + booking.getRoom().getRoomNumber() + " đã trả lại để bán"
                    + (booking.getDepositPaidAmount().signum() > 0
                    ? ". Giữ cọc " + com.hotel.util.Money.format(booking.getDepositPaidAmount()) + " VND" : ""));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "today".equals(back) ? "redirect:/admin/today" : "redirect:/admin/bookings/" + id;
    }

    // fullRefund: tick "Resort huy - hoan 100% coc" (het phong, su co phia khach san) thay vi ap chinh sach huy
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean fullRefund,
                         RedirectAttributes redirectAttributes) {
        try {
            Booking booking = bookingService.cancelBookingByStaff(id, fullRefund);
            if (booking.isRefundPending()) {
                redirectAttributes.addFlashAttribute("successMessage", "Đã hủy đơn. Cần hoàn cho khách "
                        + com.hotel.util.Money.format(booking.getRefundAmount()) + " VND (" + booking.getRefundPercent() + "% cọc)");
            }
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
    }

    // Da chuyen tra tien hoan coc cho khach -> ghi nhan, gui email bao khach
    @PostMapping("/{id}/confirm-refund")
    public String confirmRefund(@PathVariable Long id, @RequestParam(required = false) String refundNote,
                                RedirectAttributes redirectAttributes) {
        try {
            bookingService.confirmRefund(id, refundNote);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ghi nhận hoàn cọc cho khách");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
    }
}
