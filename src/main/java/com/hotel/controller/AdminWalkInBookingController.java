package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.service.BookingService;
import com.hotel.service.ComboService;
import com.hotel.service.CustomerManagementService;
import com.hotel.service.RoomService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin/bookings/walk-in")
public class AdminWalkInBookingController {

    private final BookingService bookingService;
    private final RoomService roomService;
    private final ComboService comboService;
    private final CustomerManagementService customerManagementService;

    public AdminWalkInBookingController(BookingService bookingService, RoomService roomService,
                                         ComboService comboService,
                                         CustomerManagementService customerManagementService) {
        this.bookingService = bookingService;
        this.roomService = roomService;
        this.comboService = comboService;
        this.customerManagementService = customerManagementService;
    }

    // Form dat phong tai quay - tim phong con trong theo ngay, chon combo, nhap thong tin khach vang lai.
    // Den tu trang "Xem & dat phong" cua 1 phong hoac tu o trong tren Lich phong (src=calendar) thi roomId/comboId
    // duoc chon san; phong do khong con trong trong khoang ngay -> canh bao, bat le tan tu chon phong khac
    @GetMapping("/new")
    public String newForm(
            @RequestParam(required = false) LocalDate checkIn,
            @RequestParam(required = false) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long comboId,
            @RequestParam(required = false) String src,
            Model model) {
        populateForm(model, checkIn, checkOut, guests, roomId, comboId, src, true);
        return "admin/bookings/walk-in-form";
    }

    @PostMapping("/new")
    public String create(
            @RequestParam String guestName,
            @RequestParam String guestEmail,
            @RequestParam String guestPhone,
            @RequestParam Long roomId,
            @RequestParam LocalDate checkIn,
            @RequestParam LocalDate checkOut,
            @RequestParam LocalTime checkInTime,
            @RequestParam LocalTime checkOutTime,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) Long comboId,
            @RequestParam(required = false) String src,
            Model model) {

        try {
            var customer = customerManagementService.findOrCreateWalkInCustomer(guestName, guestPhone);
            bookingService.createWalkInBooking(
                    customer, roomId, checkIn, checkOut, checkInTime, checkOutTime,
                    guests, comboId, guestName, guestPhone, guestEmail);
            return "calendar".equals(src) ? "redirect:/admin/room-calendar?from=" + checkIn.minusDays(1) : "redirect:/admin/bookings";
        } catch (BusinessException ex) {
            // Giu nguyen moi thu le tan da nhap de sua cho loi roi bam lai, khong phai go lai tu dau
            populateForm(model, checkIn, checkOut, guests, roomId, comboId, src, false);
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("guestName", guestName);
            model.addAttribute("guestEmail", guestEmail);
            model.addAttribute("guestPhone", guestPhone);
            model.addAttribute("selectedCheckInTime", checkInTime);
            model.addAttribute("selectedCheckOutTime", checkOutTime);
            return "admin/bookings/walk-in-form";
        }
    }

    private void populateForm(Model model, LocalDate checkIn, LocalDate checkOut, Integer guests,
                              Long roomId, Long comboId, String src, boolean showSearchError) {
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("guests", guests);
        model.addAttribute("combos", comboService.findActive());
        model.addAttribute("timeOptions", generateTimeOptions());
        model.addAttribute("preselectedRoomId", roomId);
        model.addAttribute("preselectedComboId", comboId);
        model.addAttribute("fromCalendar", "calendar".equals(src));
        model.addAttribute("selectedCheckInTime", LocalTime.of(14, 0));
        model.addAttribute("selectedCheckOutTime", LocalTime.of(12, 0));

        List<com.hotel.entity.Room> availableRooms = new ArrayList<>();
        if (checkIn != null && checkOut != null) {
            try {
                availableRooms = roomService.searchAvailableRooms(checkIn, checkOut, guests, null);
            } catch (BusinessException ex) {
                if (showSearchError) {
                    model.addAttribute("errorMessage", ex.getMessage());
                }
            }
        }
        model.addAttribute("availableRooms", availableRooms);

        // Phong chon san khong co trong danh sach trong -> bao ro, khong de dropdown tu nhay sang phong khac
        boolean preselectedAvailable = roomId != null && availableRooms.stream().anyMatch(r -> r.getId().equals(roomId));
        model.addAttribute("preselectedAvailable", preselectedAvailable);
        if (roomId != null && !preselectedAvailable && checkIn != null && checkOut != null) {
            try {
                model.addAttribute("unavailableRoom", roomService.findById(roomId));
            } catch (BusinessException ignored) {
                // phong da bi xoa - bo qua canh bao
            }
        }
    }

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
