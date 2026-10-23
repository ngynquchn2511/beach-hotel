package com.hotel.controller;

import com.hotel.entity.GiftVoucher;
import com.hotel.exception.BusinessException;
import com.hotel.service.GiftVoucherService;
import com.hotel.service.PaymentService;
import com.hotel.service.UserMessages;
import com.hotel.service.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;

// Mua the qua tang (bat buoc dang nhap tai khoan khach hang): chon menh gia -> thanh toan VNPay / chuyen khoan -> ma gui email nguoi nhan
@Controller
public class GiftVoucherController {

    static final List<Integer> PRESETS = List.of(500_000, 1_000_000, 2_000_000, 5_000_000);

    private final GiftVoucherService giftVoucherService;
    private final PaymentService paymentService;
    private final VnPayService vnPayService;
    private final UserMessages messages;

    public GiftVoucherController(GiftVoucherService giftVoucherService, PaymentService paymentService,
                                 VnPayService vnPayService, UserMessages messages) {
        this.giftVoucherService = giftVoucherService;
        this.paymentService = paymentService;
        this.vnPayService = vnPayService;
        this.messages = messages;
    }

    @GetMapping("/gift-vouchers")
    public String form(Model model) {
        model.addAttribute("presets", PRESETS);
        model.addAttribute("minAmount", GiftVoucherService.MIN_AMOUNT);
        model.addAttribute("maxAmount", GiftVoucherService.MAX_AMOUNT);
        return "gift-vouchers/index";
    }

    @PostMapping("/gift-vouchers")
    public String purchase(@RequestParam(required = false) BigDecimal amount,
                           @RequestParam(required = false) String purchaserName, @RequestParam(required = false) String purchaserEmail,
                           @RequestParam(required = false) String recipientName, @RequestParam(required = false) String recipientEmail,
                           @RequestParam(required = false) String message, RedirectAttributes redirectAttributes) {
        try {
            GiftVoucher v = giftVoucherService.purchase(amount, purchaserName, purchaserEmail, recipientName, recipientEmail, message);
            return "redirect:/gift-vouchers/" + v.getId() + "?token=" + v.getAccessToken();
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", messages.of(ex));
            return "redirect:/gift-vouchers";
        }
    }

    @GetMapping("/gift-vouchers/{id}")
    public String pay(@PathVariable Long id, @RequestParam(required = false) String token, Model model) {
        GiftVoucher v = giftVoucherService.findForPurchaser(id, token);
        model.addAttribute("voucher", v);
        model.addAttribute("token", token);
        model.addAttribute("vnpayEnabled", vnPayService.isEnabled());
        if (v.getStatus() == GiftVoucher.Status.PENDING_PAYMENT) {
            model.addAttribute("qrUrl", paymentService.buildGiftVoucherQrUrl(v));
            model.addAttribute("bankId", paymentService.getBankId());
            model.addAttribute("accountNo", paymentService.getAccountNo());
            model.addAttribute("accountName", paymentService.getAccountName());
        }
        return "gift-vouchers/pay";
    }

    @PostMapping("/gift-vouchers/{id}/vnpay")
    public String vnpay(@PathVariable Long id, @RequestParam(required = false) String token,
                        HttpServletRequest request, RedirectAttributes redirectAttributes) {
        GiftVoucher v = giftVoucherService.findForPurchaser(id, token);
        try {
            String returnUrl = ServletUriComponentsBuilder.fromContextPath(request).path("/payment/vnpay/return").build().toUriString();
            return "redirect:" + vnPayService.createVoucherPaymentUrl(v, request.getRemoteAddr(), returnUrl,
                    "en".equals(LocaleContextHolder.getLocale().getLanguage()));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", messages.of(ex));
            return "redirect:/gift-vouchers/" + id + "?token=" + token;
        }
    }

    // Trang cam on sau VNPay (khong lo ma the: ma chi gui qua email nguoi nhan)
    @GetMapping("/gift-vouchers/{id}/paid")
    public String paid() {
        return "gift-vouchers/paid";
    }
}
