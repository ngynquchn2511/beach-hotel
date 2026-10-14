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
    // Neu den tu trang "Xem & dat phong" cua 1 phong cu the thi roomId/comboId se duoc dien san.
    @GetMapping("/new")
    public String newForm(
            @RequestParam(required = false) LocalDate checkIn,
            @RequestParam(required = false) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long comboId,
            Model model) {

        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("guests", guests);
        model.addAttribute("combos", comboService.findActive());
        model.addAttribute("timeOptions", generateTimeOptions());
        model.addAttribute("preselectedRoomId", roomId);
        model.addAttribute("preselectedComboId", comboId);

        List<com.hotel.entity.Room> availableRooms = new ArrayList<>();
        if (checkIn != null && checkOut != null) {
            try {
                availableRooms = roomService.searchAvailableRooms(checkIn, checkOut, guests, null);
            } catch (BusinessException ex) {
                model.addAttribute("errorMessage", ex.getMessage());
            }
        }
        model.addAttribute("availableRooms", availableRooms);

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
            Model model) {

        try {
            var customer = customerManagementService.findOrCreateWalkInCustomer(guestName, guestPhone);
            bookingService.createWalkInBooking(
                    customer, roomId, checkIn, checkOut, checkInTime, checkOutTime,
                    guests, comboId, guestName, guestPhone, guestEmail);
            return "redirect:/admin/bookings";
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("checkIn", checkIn);
            model.addAttribute("checkOut", checkOut);
            model.addAttribute("guests", guests);
            model.addAttribute("combos", comboService.findActive());
            model.addAttribute("timeOptions", generateTimeOptions());
            List<com.hotel.entity.Room> availableRooms;
            try {
                availableRooms = roomService.searchAvailableRooms(checkIn, checkOut, guests, null);
            } catch (BusinessException ignored) {
                availableRooms = new ArrayList<>();
            }
            model.addAttribute("availableRooms", availableRooms);
            return "admin/bookings/walk-in-form";
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
