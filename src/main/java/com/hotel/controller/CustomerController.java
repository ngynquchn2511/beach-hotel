package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.service.BookingService;
import com.hotel.service.ComboService;
import com.hotel.service.PricingService;
import com.hotel.service.PromotionService;
import com.hotel.service.ReviewService;
import com.hotel.service.RoomService;
import com.hotel.service.UserMessages;
import com.hotel.util.Pager;
import com.hotel.util.RoomListing;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    // So phong goi y o muc "Phong tuong tu" duoi trang chi tiet
    static final int SIMILAR_ROOMS = 3;

    // So ngay toi co gia tren lich chon ngay (trang chi tiet phong)
    static final int CALENDAR_PRICE_DAYS = 366;

    // So phong moi trang ket qua tim phong
    static final int ROOMS_PER_PAGE = 10;

    private final RoomService roomService;
    private final PromotionService promotionService;
    private final RoomTypeRepository roomTypeRepository;
    private final ComboService comboService;
    private final ReviewService reviewService;
    private final BookingService bookingService;
    private final PricingService pricingService;
    private final UserMessages userMessages;

    public CustomerController(RoomService roomService, RoomTypeRepository roomTypeRepository,
                               ComboService comboService, ReviewService reviewService,
                               BookingService bookingService, PricingService pricingService, UserMessages userMessages,
                               PromotionService promotionService) {
        this.promotionService = promotionService;
        this.userMessages = userMessages;
        this.roomService = roomService;
        this.roomTypeRepository = roomTypeRepository;
        this.comboService = comboService;
        this.reviewService = reviewService;
        this.bookingService = bookingService;
        this.pricingService = pricingService;
    }

    private com.hotel.service.StayRuleService stayRuleService;

    @org.springframework.beans.factory.annotation.Autowired
    void setStayRuleService(com.hotel.service.StayRuleService stayRuleService) {
        this.stayRuleService = stayRuleService;
    }

    // Trang tim phong - hien form tim kiem, neu co du checkIn/checkOut thi hien ket qua
    @GetMapping("/customer/rooms")
    public String searchRooms(
            @RequestParam(required = false) LocalDate checkIn,
            @RequestParam(required = false) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) Long roomTypeId,
            @RequestParam(required = false) String price,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            Model model) {

        model.addAttribute("roomTypes", roomTypeRepository.findAll());
        model.addAttribute("price", price);
        model.addAttribute("sort", sort);
        model.addAttribute("priceRanges", RoomListing.PriceRange.values());
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
                // Luat ban phong: an loai phong khong dat duoc cho ngay nay va bao ly do (VD dip le can o toi thieu 2 dem)
                if (stayRuleService != null) {
                    java.util.Map<Long, java.util.Optional<BusinessException>> byType = new java.util.HashMap<>();
                    java.util.Set<String> notices = new java.util.LinkedHashSet<>();
                    LocalDate today = LocalDate.now();
                    rooms = rooms.stream().filter(r -> {
                        var v = byType.computeIfAbsent(r.getRoomType().getId(),
                                id -> stayRuleService.violation(r.getRoomType(), checkIn, checkOut, today));
                        v.ifPresent(ex -> notices.add(userMessages.of(ex)));
                        return v.isEmpty();
                    }).toList();
                    model.addAttribute("ruleNotices", notices);
                }
                // Loc gia + sap xep tren toan bo ket qua roi moi cat trang
                Pager<com.hotel.entity.Room> pager = Pager.of(RoomListing.apply(rooms, price, sort), page, ROOMS_PER_PAGE);
                model.addAttribute("pager", pager);
                model.addAttribute("rooms", pager.items());
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
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        var room = roomService.findById(id);
        model.addAttribute("room", room);
        // Gia thanh vien: khach dang nhap thay gia cua minh, khach vang lai thay muc giam toi da neu dang nhap
        if (userDetails != null && userDetails.getUser().getRole() == com.hotel.entity.UserRole.CUSTOMER) {
            int percent = pricingService.memberDiscountPercent(userDetails.getUser());
            if (percent > 0) {
                model.addAttribute("memberPercent", percent);
                model.addAttribute("memberPrice", room.getPrice().subtract(
                        com.hotel.service.PricingService.memberDiscountAmount(room.getPrice(), percent)));
            }
        } else if (userDetails == null && pricingService.getSettings().getMaxMemberDiscount() > 0) {
            model.addAttribute("memberMaxPercent", pricingService.getSettings().getMaxMemberDiscount());
        }
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
        // Gia tung dem 12 thang toi (gom phu thu cuoi tuan / dip le) de hien ngay tren lich chon ngay
        model.addAttribute("calendarPrices", pricingService.calendarPrices(room.getPrice(), LocalDate.now(), CALENDAR_PRICE_DAYS));
        model.addAttribute("combos", comboService.findActive());
        model.addAttribute("selectedComboId", comboId);
        // Danh gia cua khach da o cung loai phong (diem trung binh + 5 nhan xet moi nhat)
        model.addAttribute("ratingStats", reviewService.statsForRoomType(room.getRoomType().getId()));
        model.addAttribute("roomReviews", reviewService.findLatestForRoomType(room.getRoomType().getId(), 5));
        model.addAttribute("similarRooms", roomService.findSimilarRooms(room, checkIn, checkOut, guests, SIMILAR_ROOMS));
        // Bang ron cac khuyen mai dang chay (dat online thoa dieu kien la tu giam)
        model.addAttribute("activePromotions", promotionService.findActive());
        // Form "Bao toi khi phong trong lai" dien san email khach da dang nhap
        model.addAttribute("alertEmail", userDetails != null ? userDetails.getUser().getEmail() : null);
        // Chi noi "con trong dung ngay ban chon" khi ngay hop le (ngay sai thi goi y khong loc theo ngay)
        model.addAttribute("similarByDates", RoomService.isValidStay(checkIn, checkOut));

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

                // Khuyen mai tu dong (o dai ngay / dat som / gio chot) - giong luc dat online
                var promotion = promotionService.best(roomAmount, checkIn, checkOut, LocalDate.now()).orElse(null);
                model.addAttribute("promotion", promotion);
                var totalAmount = roomAmount.add(comboAmount)
                        .subtract(promotion != null ? promotion.amount() : java.math.BigDecimal.ZERO);
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
