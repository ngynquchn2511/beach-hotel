package com.hotel.controller;

import com.hotel.entity.GiftVoucher;
import com.hotel.exception.BusinessException;
import com.hotel.service.GiftVoucherService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// The qua tang: xem the da ban, xac nhan tien chuyen khoan, huy the chua thanh toan (ADMIN + STAFF)
@Controller
@RequestMapping("/admin/gift-vouchers")
public class AdminGiftVoucherController {

    private final GiftVoucherService giftVoucherService;

    public AdminGiftVoucherController(GiftVoucherService giftVoucherService) {
        this.giftVoucherService = giftVoucherService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        Pager<GiftVoucher> pager = Pager.of(giftVoucherService.all(), page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("pager", pager);
        model.addAttribute("vouchers", pager.items());
        return "admin/gift-vouchers/list";
    }

    @PostMapping("/{id}/confirm-transfer")
    public String confirm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            GiftVoucher v = giftVoucherService.confirmBankTransfer(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã kích hoạt thẻ " + v.getCode() + " và gửi email người nhận");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/gift-vouchers";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            giftVoucherService.cancel(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy thẻ chưa thanh toán");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/gift-vouchers";
    }
}
