package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.service.BookingService;
import com.hotel.service.ComboService;
import com.hotel.service.PricingService;
import com.hotel.service.ReviewService;
import com.hotel.service.RoomService;
import com.hotel.service.UserMessages;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
public class CustomerController {

    private final RoomService roomService;
    private final RoomTypeRepository roomTypeRepository;
    private final ComboService comboService;
    private final ReviewService reviewService;
    private final BookingService bookingService;
    private final PricingService pricingService;
    private final UserMessages userMessages;

    public CustomerController(RoomService roomService, RoomTypeRepository roomTypeRepository,
                               ComboService comboService, ReviewService reviewService,
                               BookingService bookingService, PricingService pricingService, UserMessages userMessages) {
        this.userMessages = userMessages;
        this.roomService = roomService;
        this.roomTypeRepository = roomTypeRepository;
        this.comboService = comboService;
        this.reviewService = reviewService;
        this.bookingService = bookingService;
        this.pricingService = pricingService;
    }

    // Trang tim phong - hien form tim kiem, neu co du checkIn/checkOut thi hien ket qua
    @GetMapping("/customer/rooms")
    public String searchRooms(
            @RequestParam(required = false) LocalDate checkIn,
            @RequestParam(required = false) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) Long roomTypeId,
            Model model) {

        model.addAttribute("roomTypes", roomTypeRepository.findAll());
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("guests", guests);
        model.addAttribute("roomTypeId", roomTypeId);
        model.addAttribute("today", LocalDate.now());

        boolean searched = checkIn != null || checkOut != null;
        model.addAttribute("searched", searched);

        if (searched) {
            try {
                List<com.hotel.entity.Room> rooms = roomService.searchAvailableRooms(checkIn, checkOut, guests, roomTypeId);
                model.addAttribute("rooms", rooms);
            } catch (BusinessException ex) {
                model.addAttribute("errorMessage", userMessages.of(ex));
            }
        }

        return "customer/rooms";
    }

    // Xem chi tiet phong, giu lai ngay da chon (neu co) de tinh tong tien va kiem tra con trong
    // Cho phep chon combo dich vu di kem (an nuong / lau / phong thuong)
    @GetMapping("/customer/rooms/{id}")
    public String viewRoomDetail(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate checkIn,
            @RequestParam(required = false) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) Long comboId,
            Model model) {

        var room = roomService.findById(id);
        model.addAttribute("room", room);
        model.addAttribute("unavailableDates", roomService.findUnavailableDates(id).stream()
                .map(LocalDate::toString)
                .toList());
        // Ngay chi co don cho xac nhan giu cho -> lich to mau rieng
        model.addAttribute("pendingDates", roomService.findPendingOnlyDates(id).stream()
                .map(LocalDate::toString)
                .toList());
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("guests", guests);
        model.addAttribute("combos", comboService.findActive());
        model.addAttribute("selectedComboId", comboId);
        // Danh gia cua khach da o cung loai phong (diem trung binh + 5 nhan xet moi nhat)
        model.addAttribute("ratingStats", reviewService.statsForRoomType(room.getRoomType().getId()));
        model.addAttribute("roomReviews", reviewService.findLatestForRoomType(room.getRoomType().getId(), 5));

        if (checkIn != null && checkOut != null) {
            try {
                if (checkIn.isBefore(LocalDate.now())) {
                    throw BusinessException.of("err.checkInPast");
                }
                if (!checkOut.isAfter(checkIn)) {
                    throw BusinessException.of("err.checkOutAfterCheckIn");
                }
                long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
                // Tinh theo tung dem (cuoi tuan / ngay le co phu thu) - khop voi so tien luc dat
                var roomAmount = pricingService.roomAmount(room.getPrice(), checkIn, checkOut);

                // Neu khach da chon combo thi cong them gia combo (tinh 1 lan, khong nhan theo dem)
                var comboAmount = java.math.BigDecimal.ZERO;
                if (comboId != null) {
                    var combo = comboService.findById(comboId);
                    comboAmount = combo.getPrice();
                    model.addAttribute("selectedCombo", combo);
                }

                var totalAmount = roomAmount.add(comboAmount);
                // Kiem tra chi tiet: bao tri / trung don (cho xac nhan hay da xac nhan) / phong trong lai tu luc nao
                BookingService.Availability availability = bookingService.checkAvailability(room, checkIn, checkOut);
                boolean available = availability.isAvailable();
                model.addAttribute("availability", availability);
                model.addAttribute("availabilityMessage", availability.describe(userMessages::get));
                if (!available && availability.freeFrom() != null && !availability.maintenance()) {
                    // Goi y dat lai tu ngay phong trong, giu nguyen so dem
                    LocalDate suggestIn = availability.freeFrom().toLocalDate();
                    model.addAttribute("suggestCheckIn", suggestIn);
                    model.addAttribute("suggestCheckOut", suggestIn.plusDays(nights));
                }

                model.addAttribute("nights", nights);
                model.addAttribute("roomAmount", roomAmount);
                model.addAttribute("comboAmount", comboAmount);
                model.addAttribute("totalAmount", totalAmount);
                model.addAttribute("available", available);
            } catch (BusinessException ex) {
                model.addAttribute("errorMessage", userMessages.of(ex));
            }
        }

        return "customer/room-detail";
    }
}
