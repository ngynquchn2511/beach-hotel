package com.hotel.controller;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.ChargeType;
import com.hotel.entity.Gender;
import com.hotel.entity.IdDocumentType;
import com.hotel.entity.PaymentMethod;
import com.hotel.exception.BusinessException;
import com.hotel.service.BookingService;
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

    public AdminBookingController(BookingService bookingService, PaymentService paymentService,
                                  GuestRegistrationService guestRegistrationService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.guestRegistrationService = guestRegistrationService;
    }

    // Danh sach toan bo booking - dung lam "lich su dat phong" va man hinh check-in/check-out cho STAFF/ADMIN
    // Co the loc theo trang thai qua query param ?status=PENDING, va tim theo ten/email/sdt qua ?q=
    @GetMapping
    public String list(@RequestParam(required = false) BookingStatus status,
                        @RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "1") int page, Model model) {
        List<Booking> all = bookingService.findAllBookings(status, q);
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
        model.addAttribute("canEditGuests", guestRegistrationService.canEdit(booking));
        model.addAttribute("genders", Gender.values());
        model.addAttribute("idTypes", IdDocumentType.values());
        if (booking.isAwaitingDeposit()) {
            model.addAttribute("depositTransferContent", paymentService.depositTransferContent(booking));
        }
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
    public String checkIn(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bookingService.checkIn(id);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
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
        model.addAttribute("chargeTypes", ChargeType.values());
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

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bookingService.cancelBookingByStaff(id);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
    }
}
