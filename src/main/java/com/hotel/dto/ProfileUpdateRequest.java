package com.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Khach tu cap nhat thong tin ca nhan - email la ten dang nhap nen khong cho doi
@Getter
@Setter
public class ProfileUpdateRequest {

    @NotBlank(message = "{val.fullName.required}")
    @Size(max = 150, message = "{val.fullName.max}")
    private String fullName;

    @NotBlank(message = "{val.phone.required}")
    @Pattern(regexp = "^0[0-9]{9}$", message = "{val.phone.pattern}")
    private String phoneNumber;
}
