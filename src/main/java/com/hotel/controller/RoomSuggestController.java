package com.hotel.controller;

import com.hotel.entity.GuestPreference;
import com.hotel.entity.UserRole;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.RoomSuggestionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

// "Goi y phong cho ban": khach tra loi vai cau ve so thich -> 3 loai phong hop nhat kem ly do.
// Ai cung xem duoc; khach da dang nhap thi luu so thich (dien san lan sau, le tan xem de chuan bi phong)
@Controller
public class RoomSuggestController {

    // Muc ngan sach / dem cho khach chon nhanh (0 = khong gioi han)
    static final List<Long> BUDGETS = List.of(0L, 1_000_000L, 1_500_000L, 2_000_000L, 3_500_000L);

    private final RoomSuggestionService service;

    public RoomSuggestController(RoomSuggestionService service) {
        this.service = service;
    }

    @GetMapping("/rooms/suggest")
    public String suggest(@RequestParam(required = false) Integer guests,
                          @RequestParam(required = false) GuestPreference.Purpose purpose,
                          @RequestParam(required = false) Long budget,
                          @RequestParam(required = false) GuestPreference.View view,
                          @RequestParam(required = false) List<String> priorities,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                          @RequestParam(required = false) String note,
                          @AuthenticationPrincipal CustomUserDetails user, Model model) {
        boolean customer = user != null && user.getUser().getRole() == UserRole.CUSTOMER;
        model.addAttribute("purposes", GuestPreference.Purpose.values());
        model.addAttribute("views", GuestPreference.View.values());
        model.addAttribute("allPriorities", GuestPreference.Priority.values());
        model.addAttribute("budgets", BUDGETS);
        model.addAttribute("loggedInCustomer", customer);

        if (guests == null) {
            // Chua tra loi: dien san so thich da luu (neu co)
            GuestPreference saved = customer ? service.preferenceOf(user.getUser().getId()).orElse(null) : null;
            model.addAttribute("form", saved != null
                    ? new Form(saved.getGuests(), saved.getPurpose(), saved.getMaxBudget(), saved.getPreferredView(),
                    saved.getPrioritySet().stream().map(Enum::name).toList(), null, null, saved.getNote())
                    : new Form(2, null, null, GuestPreference.View.ANY, List.of(), null, null, null));
            model.addAttribute("savedPreference", saved != null);
            return "rooms-suggest";
        }

        Set<GuestPreference.Priority> pr = GuestPreference.parse(priorities);
        RoomSuggestionService.Answers answers = new RoomSuggestionService.Answers(guests, purpose, budget, view, pr, checkIn, checkOut);
        model.addAttribute("form", new Form(answers.guests(), purpose, answers.maxBudget(), answers.view(),
                pr.stream().map(Enum::name).toList(), checkIn, checkOut, note));
        model.addAttribute("result", service.suggest(answers));
        model.addAttribute("datesIgnored", (checkIn != null || checkOut != null) && !answers.hasDates());
        if (customer) {
            service.save(user.getUser(), answers, note);
            model.addAttribute("preferenceSaved", true);
        }
        return "rooms-suggest";
    }

    public record Form(Integer guests, GuestPreference.Purpose purpose, Long budget, GuestPreference.View view,
                       List<String> priorities, LocalDate checkIn, LocalDate checkOut, String note) {
    }
}
