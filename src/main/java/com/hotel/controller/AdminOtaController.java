package com.hotel.controller;

import com.hotel.entity.Booking;
import com.hotel.entity.OtaBlock;
import com.hotel.entity.OtaFeed;
import com.hotel.entity.Room;
import com.hotel.exception.BusinessException;
import com.hotel.repository.RoomRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.BookingService;
import com.hotel.service.CustomerManagementService;
import com.hotel.service.IcalService;
import com.hotel.service.OtaSyncService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

// Dong bo 2 chieu voi Booking.com / Airbnb: link lich cua kenh (chieu vao), link xuat cho kenh (chieu ra),
// danh sach luot dat tu kenh, canh bao trung lich, ghi nhan thanh don. Them / xoa link chi ADMIN (SecurityConfig)
@Controller
@RequestMapping("/admin/ota")
public class AdminOtaController {

    private final OtaSyncService syncService;
    private final BookingService bookingService;
    private final CustomerManagementService customerService;
    private final IcalService icalService;
    private final RoomRepository roomRepository;

    public AdminOtaController(OtaSyncService syncService, BookingService bookingService, CustomerManagementService customerService,
                              IcalService icalService, RoomRepository roomRepository) {
        this.syncService = syncService;
        this.bookingService = bookingService;
        this.customerService = customerService;
        this.icalService = icalService;
        this.roomRepository = roomRepository;
    }

    @GetMapping
    public String page(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "1") int feedPage,
                       @RequestParam(defaultValue = "1") int roomPage, Model model) {
        List<Room> rooms = roomRepository.findAll().stream().sorted(Comparator.comparing(Room::getRoomNumber)).toList();
        model.addAttribute("rooms", rooms);
        model.addAttribute("roomPager", com.hotel.util.Pager.of(rooms, roomPage, com.hotel.util.PaginationUtil.DEFAULT_PAGE_SIZE, "roomPage"));
        model.addAttribute("channels", OtaFeed.Channel.values());
        var feeds = syncService.feeds();
        model.addAttribute("feeds", feeds);
        model.addAttribute("feedPager", com.hotel.util.Pager.of(feeds, feedPage, com.hotel.util.PaginationUtil.DEFAULT_PAGE_SIZE, "feedPage"));
        List<OtaSyncService.Row> rows = syncService.upcoming();
        model.addAttribute("rows", rows);
        model.addAttribute("rowPager", com.hotel.util.Pager.of(rows, page, com.hotel.util.PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("conflictCount", rows.stream().filter(OtaSyncService.Row::conflict).count());
        model.addAttribute("removedCount", rows.stream().filter(r -> r.block().getRemovedAt() != null && r.block().getBooking() != null).count());
        model.addAttribute("exportBase", ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/ical/rooms/");
        model.addAttribute("exportKey", icalService.token());
        return "admin/ota/index";
    }

    @PostMapping("/feeds")
    public String addFeed(@RequestParam(required = false) Long roomId, @RequestParam(required = false) OtaFeed.Channel channel,
                          @RequestParam(required = false) String url, RedirectAttributes ra) {
        return run(() -> {
            OtaFeed f = syncService.addFeed(roomId, channel, url);
            try {
                syncService.sync(f.getId());
            } catch (BusinessException ex) {
                throw new BusinessException("Đã lưu link nhưng chưa đọc được lịch: " + ex.getMessage());
            }
        }, "Đã thêm link và đồng bộ lần đầu", ra);
    }

    @PostMapping("/feeds/{id}/sync")
    public String sync(@PathVariable Long id, RedirectAttributes ra) {
        try {
            OtaSyncService.SyncResult r = syncService.sync(id);
            ra.addFlashAttribute("successMessage", "Đã đồng bộ: " + r.events() + " lượt đặt trên kênh, thêm " + r.added()
                    + ", đổi " + r.updated() + ", bỏ " + r.removed() + (r.conflicts().isEmpty() ? "" : ". TRÙNG LỊCH: " + String.join("; ", r.conflicts())));
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/ota";
    }

    @PostMapping("/sync-all")
    public String syncAll(RedirectAttributes ra) {
        int ok = syncService.syncAll();
        ra.addFlashAttribute("successMessage", "Đã đồng bộ " + ok + " link lịch. Link lỗi (nếu có) xem ở cột trạng thái.");
        return "redirect:/admin/ota";
    }

    @PostMapping("/feeds/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        return run(() -> syncService.toggle(id), "Đã cập nhật trạng thái link", ra);
    }

    @PostMapping("/feeds/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        return run(() -> syncService.deleteFeed(id), "Đã xóa link - các ngày khóa theo link này được mở bán lại", ra);
    }

    // Le tan ghi nhan luot dat tu kenh thanh don (khi khach toi hoac khi kenh gui thong tin khach)
    @PostMapping("/blocks/{id}/link")
    public String link(@PathVariable Long id, @RequestParam(required = false) String guestName, @RequestParam(required = false) String guestPhone,
                       @RequestParam(required = false) String guestEmail, @RequestParam(required = false) Integer guests,
                       @RequestParam(required = false) BigDecimal amount, @RequestParam(required = false) String note,
                       @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes ra) {
        try {
            OtaBlock block = syncService.block(id);
            if (guestName == null || guestName.isBlank() || guestPhone == null || guestPhone.isBlank()) {
                throw new BusinessException("Vui lòng nhập họ tên và số điện thoại khách");
            }
            var customer = customerService.findOrCreateWalkInCustomer(guestName.trim(), guestPhone.trim());
            Booking b = bookingService.createOtaBooking(block, customer, guestName, guestPhone, guestEmail, guests, amount, note);
            ra.addFlashAttribute("successMessage", "Đã tạo đơn #" + b.getId() + " từ lượt đặt " + block.getFeed().getChannel().getLabel());
            return "redirect:/admin/bookings/" + b.getId();
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/ota";
        }
    }

    @PostMapping("/blocks/{id}/dismiss")
    public String dismiss(@PathVariable Long id, RedirectAttributes ra) {
        return run(() -> syncService.dismissRemoved(id), "Đã đánh dấu đã xử lý", ra);
    }

    private static String run(Runnable action, String success, RedirectAttributes ra) {
        try {
            action.run();
            ra.addFlashAttribute("successMessage", success);
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/ota";
    }
}
