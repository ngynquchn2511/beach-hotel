package com.hotel.controller;

import com.hotel.entity.CashShift;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.CashShiftService;
import com.hotel.service.PaymentService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

// So quy ca + ban giao ca cho le tan (ADMIN + STAFF)
@Controller
@RequestMapping("/admin/shifts")
public class AdminCashShiftController {

    private final CashShiftService cashShiftService;

    public AdminCashShiftController(CashShiftService cashShiftService) {
        this.cashShiftService = cashShiftService;
    }

    @GetMapping
    public String index(@RequestParam(defaultValue = "1") int page, Model model) {
        cashShiftService.current().ifPresentOrElse(shift -> {
            List<PaymentService.LedgerEntry> ledger = cashShiftService.ledger(shift);
            BigDecimal cashIn = PaymentService.totalsByMethod(ledger).get(com.hotel.entity.PaymentMethod.CASH);
            model.addAttribute("current", shift);
            model.addAttribute("ledger", ledger);
            model.addAttribute("totals", PaymentService.totalsByMethod(ledger));
            model.addAttribute("expectedCash", shift.getOpeningCash().add(cashIn));
        }, () -> cashShiftService.lastClosed().ifPresent(last -> model.addAttribute("lastClosed", last)));
        Pager<CashShift> pager = Pager.of(cashShiftService.all().stream().filter(s -> !s.isOpen()).toList(), page,
                PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("pager", pager);
        model.addAttribute("history", pager.items());
        model.addAttribute("noteRequiredDiff", CashShiftService.NOTE_REQUIRED_DIFF);
        return "admin/shifts/index";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        CashShift shift = cashShiftService.find(id);
        model.addAttribute("shift", shift);
        model.addAttribute("ledger", cashShiftService.ledger(shift));
        return "admin/shifts/detail";
    }

    @PostMapping("/open")
    public String open(@RequestParam(required = false) BigDecimal openingCash, @AuthenticationPrincipal CustomUserDetails user,
                       RedirectAttributes redirectAttributes) {
        try {
            cashShiftService.open(user.getUser(), openingCash);
            redirectAttributes.addFlashAttribute("successMessage", "Đã nhận ca");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/shifts";
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, @RequestParam(required = false) BigDecimal countedCash,
                        @RequestParam(required = false) BigDecimal cashToSafe, @RequestParam(required = false) String handoverNote,
                        @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        try {
            CashShift shift = cashShiftService.close(id, user.getUser(), countedCash, cashToSafe, handoverNote);
            redirectAttributes.addFlashAttribute("successMessage", "Đã đóng ca #" + shift.getId());
            return "redirect:/admin/shifts/" + shift.getId();
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/shifts";
        }
    }
}
