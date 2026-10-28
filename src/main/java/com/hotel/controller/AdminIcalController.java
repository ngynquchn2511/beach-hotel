package com.hotel.controller;

import com.hotel.entity.Room;
import com.hotel.repository.RoomRepository;
import com.hotel.service.IcalService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Comparator;
import java.util.List;

// Trang "Dong bo lich (iCal)": link .ics tung phong / tat ca phong de dan vao Google Calendar, Booking.com (chi ADMIN)
@Controller
public class AdminIcalController {

    private final IcalService icalService;
    private final RoomRepository roomRepository;

    public AdminIcalController(IcalService icalService, RoomRepository roomRepository) {
        this.icalService = icalService;
        this.roomRepository = roomRepository;
    }

    @GetMapping("/admin/ical")
    public String page(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page, Model model) {
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String key = icalService.token();
        List<Room> rooms = roomRepository.findAll().stream().sorted(Comparator.comparing(Room::getRoomNumber)).toList();
        model.addAttribute("rooms", rooms);
        model.addAttribute("roomPager", com.hotel.util.Pager.of(rooms, page, com.hotel.util.PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("baseUrl", base);
        model.addAttribute("icalKey", key);
        model.addAttribute("allUrl", base + "/ical/all.ics?key=" + key);
        return "admin/ical/index";
    }

    @PostMapping("/admin/ical/rotate")
    public String rotate(RedirectAttributes redirectAttributes) {
        icalService.rotateToken();
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã đổi mã. Link cũ ngừng hoạt động - hãy cập nhật link mới ở Google Calendar / Booking.com");
        return "redirect:/admin/ical";
    }
}
