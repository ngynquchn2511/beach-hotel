package com.hotel.service;

import com.hotel.exception.BusinessException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

// Lay cau thong bao cho khach theo ngon ngu dang chon (nut VI/EN) - dung trong controller trang khach
@Component
public class UserMessages {

    private final MessageSource messageSource;

    public UserMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String code, Object... args) {
        return messageSource.getMessage(code, args, code, LocaleContextHolder.getLocale());
    }

    // Loi co ma -> dich; loi chi co cau tieng Viet (chua chuyen sang ma) -> giu nguyen
    public String of(BusinessException ex) {
        return ex.render(this::get);
    }
}
