package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.service.FileStorageService;
import com.hotel.service.UserMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

// Bat BusinessException chua duoc xu ly rieng trong tung controller (vd: GET xem chi tiet 1 ban ghi
// theo id khong ton tai: roomService.findById, discountCodeService.findById...). Neu khong co lop nay,
// loi se roi thang xuong thanh trang loi may chu (500) rat xau cho nguoi dung, thay vi mot thong bao than thien.
// Cac noi da tu bat BusinessException bang try/catch rieng (AuthController, CustomerBookingController,
// AdminBookingController...) khong bi anh huong vi exception do khong con "chua duoc xu ly" nua.
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final UserMessages userMessages;

    public GlobalExceptionHandler(UserMessages userMessages) {
        this.userMessages = userMessages;
    }

    // Tep tai len vuot gioi han cua server (VD video qua lon): quay lai trang truoc kem thong bao
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleUploadTooLarge(HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("errorMessage", userMessages.get("err.uploadTooLarge",
                FileStorageService.MAX_IMAGE_MB, FileStorageService.MAX_VIDEO_MB));
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null && !referer.isBlank() ? referer : "/");
    }

    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusinessException(BusinessException ex, HttpServletResponse response) {
        log.warn("BusinessException chua duoc xu ly rieng, hien thi trang loi than thien: {}", ex.getMessage());
        response.setStatus(HttpStatus.NOT_FOUND.value());
        ModelAndView mav = new ModelAndView("error/business-error");
        mav.addObject("errorMessage", userMessages.of(ex));
        return mav;
    }
}
