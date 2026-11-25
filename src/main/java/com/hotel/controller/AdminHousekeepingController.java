package com.hotel.controller;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.HousekeepingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

// Bang don phong (ADMIN, STAFF, HOUSEKEEPER). Giao phong / kiem phong / cai dat chi le tan, quan ly (SecurityConfig)
@Controller
@RequestMapping("/admin/housekeeping")
public class AdminHousekeepingController {

    private final HousekeepingService housekeepingService;

    public AdminHousekeepingController(HousekeepingService housekeepingService) {
        this.housekeepingService = housekeepingService;
    }

    // mine=true: nhan vien buong chi xem phong giao cho minh + phong chua giao
    @GetMapping
    public String board(@RequestParam(defaultValue = "false") boolean mine, @AuthenticationPrincipal CustomUserDetails user, Model model) {
        boolean housekeeper = user != null && user.getUser().getRole() == UserRole.HOUSEKEEPER;
        model.addAttribute("board", housekeepingService.board(LocalDate.now(), housekeeper && mine ? user.getUser().getId() : null));
        model.addAttribute("mine", housekeeper && mine);
        model.addAttribute("isHousekeeper", housekeeper);
        model.addAttribute("myId", user != null ? user.getUser().getId() : null);
        model.addAttribute("housekeepers", housekeepingService.housekeepers());
        java.util.List<HousekeepingService.Member> team = housekeepingService.team(java.time.LocalDateTime.now());
        model.addAttribute("team", team);
        model.addAttribute("teamById", team.stream().collect(java.util.stream.Collectors.toMap(m -> m.user().getId(), m -> m)));
        model.addAttribute("inspectionRequired", housekeepingService.inspectionRequired());
        model.addAttribute("roomChecks", housekeepingService.pendingRoomChecks(housekeeper && mine ? user.getUser().getId() : null));
        return "admin/housekeeping/index";
    }

    // Chia cac phong cho don chua giao cho nhung nguoi dang trong ca (it viec nhan truoc)
    @PostMapping("/auto-assign")
    public String autoAssign(@AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        try {
            int n = housekeepingService.autoAssign(staffName(user), java.time.LocalDateTime.now());
            redirectAttributes.addFlashAttribute("successMessage", "Đã chia " + n + " phòng cho nhân viên đang trong ca");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return back(false);
    }

    // Nguoi duoc giao bam "Nhan kiem" - dang len phong
    @PostMapping("/room-checks/{bookingId}/accept")
    public String acceptRoomCheck(@PathVariable Long bookingId, @RequestParam(defaultValue = "false") boolean mine,
                                  @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        return run(() -> housekeepingService.acceptRoomCheck(bookingId, staff(user)), "Đã nhận kiểm - lễ tân biết bạn đang lên phòng",
                redirectAttributes, mine);
    }

    // Doi nguoi kiem phong truoc khi tra (yeu cau dang cho)
    @PostMapping("/room-checks/{bookingId}/assign")
    public String assignRoomCheck(@PathVariable Long bookingId, @RequestParam(required = false) Long assigneeId,
                                  @RequestParam(required = false) String back,
                                  @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        String view = run(() -> housekeepingService.assignRoomCheck(bookingId, assigneeId, staffName(user)),
                "Đã đổi người kiểm phòng", redirectAttributes, false);
        return "checkout".equals(back) ? "redirect:/admin/bookings/" + bookingId + "/check-out#kiem-phong" : view;
    }

    @PostMapping("/{roomId}/assign")
    public String assign(@PathVariable Long roomId, @RequestParam(required = false) Long assigneeId,
                         @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        return run(() -> housekeepingService.assign(roomId, assigneeId, staffName(user)),
                assigneeId != null ? "Đã giao phòng" : "Đã bỏ giao phòng", redirectAttributes, false);
    }

    @PostMapping("/{roomId}/start")
    public String start(@PathVariable Long roomId, @RequestParam(defaultValue = "false") boolean mine,
                        @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        return run(() -> housekeepingService.start(roomId, staff(user)), "Đã nhận dọn phòng", redirectAttributes, mine);
    }

    @PostMapping("/{roomId}/release")
    public String release(@PathVariable Long roomId, @RequestParam(defaultValue = "false") boolean mine,
                          RedirectAttributes redirectAttributes) {
        return run(() -> housekeepingService.release(roomId), "Đã trả phòng về danh sách chờ dọn", redirectAttributes, mine);
    }

    @PostMapping("/{roomId}/finish")
    public String finish(@PathVariable Long roomId, @RequestParam(defaultValue = "false") boolean mine,
                         @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        try {
            boolean ready = housekeepingService.finish(roomId, staff(user));
            redirectAttributes.addFlashAttribute("successMessage", ready ? "Phòng đã sạch, sẵn sàng đón khách"
                    : "Đã báo dọn xong - chờ lễ tân kiểm phòng");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return back(mine);
    }

    @PostMapping("/{roomId}/inspect/pass")
    public String pass(@PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        return run(() -> housekeepingService.pass(roomId, staffName(user)), "Kiểm đạt - phòng đã sạch", redirectAttributes, false);
    }

    @PostMapping("/{roomId}/inspect/fail")
    public String fail(@PathVariable Long roomId, @RequestParam(required = false) String note,
                       @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        return run(() -> housekeepingService.fail(roomId, note, staffName(user)), "Đã trả phòng cho buồng phòng làm lại",
                redirectAttributes, false);
    }

    // Kiem phong truoc khi tra: nhan vien buong phong bao co / khong co do mat, hu hong
    @PostMapping("/room-checks/{bookingId}")
    public String reportRoomCheck(@PathVariable Long bookingId, @RequestParam(defaultValue = "false") boolean issue,
                                  @RequestParam(required = false) String note, @RequestParam(defaultValue = "false") boolean mine,
                                  @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        return run(() -> housekeepingService.reportRoomCheck(bookingId, issue, note, staff(user)),
                "Đã báo kết quả kiểm phòng cho lễ tân", redirectAttributes, mine);
    }

    // Thong ke nang suat / chat luong buong phong (mac dinh 30 ngay gan nhat)
    @GetMapping("/stats")
    public String stats(@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
                        Model model) {
        LocalDate t = to != null ? to : LocalDate.now();
        LocalDate f = from != null ? from : t.minusDays(29);
        if (f.isAfter(t)) {
            LocalDate x = f;
            f = t;
            t = x;
        }
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("stats", housekeepingService.stats(f, t));
        return "admin/housekeeping/stats";
    }

    @PostMapping("/settings")
    public String settings(@RequestParam(defaultValue = "false") boolean inspectionRequired, RedirectAttributes redirectAttributes) {
        housekeepingService.setInspectionRequired(inspectionRequired);
        redirectAttributes.addFlashAttribute("successMessage", inspectionRequired
                ? "Đã bật kiểm phòng: buồng phòng dọn xong phải chờ lễ tân kiểm" : "Đã tắt kiểm phòng");
        return back(false);
    }

    private static User staff(CustomUserDetails user) {
        return user != null ? user.getUser() : null;
    }

    private static String staffName(CustomUserDetails user) {
        return user != null ? user.getUser().getFullName() : null;
    }

    private static String back(boolean mine) {
        return mine ? "redirect:/admin/housekeeping?mine=true" : "redirect:/admin/housekeeping";
    }

    private static String run(Runnable action, String success, RedirectAttributes redirectAttributes, boolean mine) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", success);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return back(mine);
    }
}
