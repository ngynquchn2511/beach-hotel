package com.hotel.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
public class BookingConfirmRequest {

    // So diem thuong muon dung (khach da dang nhap); null / 0 = khong dung
    private Integer pointsToUse;

    // Ma the qua tang (khong bat buoc)
    private String giftCode;

    // Gia khong hoan tien (giam them %, tra truoc toan bo)
    private Boolean nonRefundable;

    // Yeu cau dac biet
    private java.util.List<com.hotel.entity.GuestRequestTag> requestTags;

    @jakarta.validation.constraints.Size(max = 500, message = "{req.error.tooLongField}")
    private String specialRequests;

    @NotBlank(message = "{val.guestName.required}")
    private String guestName;

    @NotBlank(message = "{val.email.enter}")
    @Email(message = "{val.email.invalid}")
    private String guestEmail;

    @NotBlank(message = "{val.phone.enter}")
    @Pattern(regexp = "^0[0-9]{9}$", message = "{val.phone.pattern}")
    private String guestPhone;

    @NotNull(message = "{val.checkInTime.required}")
    private LocalTime checkInTime;

    @NotNull(message = "{val.checkOutTime.required}")
    private LocalTime checkOutTime;

    // Khong bat buoc - de trong neu khach khong dung ma giam gia
    private String discountCode;
}
