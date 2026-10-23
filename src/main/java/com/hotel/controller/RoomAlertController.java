package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.service.RoomAlertService;
import com.hotel.service.UserMessages;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

// Form "Bao toi khi phong trong lai" o trang chi tiet phong (khi khoang ngay khach chon da kin) - bat buoc dang nhap tai khoan khach hang
@Controller
public class RoomAlertController {

    private final RoomAlertService roomAlertService;
    private final UserMessages messages;

    public RoomAlertController(RoomAlertService roomAlertService, UserMessages messages) {
        this.roomAlertService = roomAlertService;
        this.messages = messages;
    }

    // Khach vang lai bam "Dang nhap de nhan thong bao": Spring Security chuyen ve /login, dang nhap xong
    // quay lai day roi ve trang phong voi dung khoang ngay de dien form
    @GetMapping("/customer/rooms/{id}/alerts")
    public String loginThenBack(@PathVariable Long id,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                                @RequestParam(required = false) Integer guests) {
        return "redirect:" + roomPage(id, checkIn, checkOut, guests) + "#dat-phong";
    }

    @PostMapping("/customer/rooms/{id}/alerts")
    public String subscribe(@PathVariable Long id, @RequestParam(required = false) String email,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                            @RequestParam(required = false) Integer guests,
                            RedirectAttributes redirectAttributes) {
        try {
            roomAlertService.subscribe(id, email, checkIn, checkOut, guests, LocaleContextHolder.getLocale().getLanguage());
            redirectAttributes.addFlashAttribute("alertSuccess", messages.get("alert.saved", email.trim()));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("alertError", messages.of(ex));
        }
        return "redirect:" + roomPage(id, checkIn, checkOut, guests) + "#dat-phong";
    }

    private static String roomPage(Long id, LocalDate checkIn, LocalDate checkOut, Integer guests) {
        return UriComponentsBuilder.fromPath("/customer/rooms/" + id)
                .queryParamIfPresent("checkIn", java.util.Optional.ofNullable(checkIn))
                .queryParamIfPresent("checkOut", java.util.Optional.ofNullable(checkOut))
                .queryParamIfPresent("guests", java.util.Optional.ofNullable(guests))
                .build().toUriString();
    }
}
