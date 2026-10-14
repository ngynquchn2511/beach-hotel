package com.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "{val.currentPassword.required}")
    private String currentPassword;

    @NotBlank(message = "{val.newPassword.required}")
    @Size(min = 6, message = "{val.newPassword.min}")
    private String newPassword;

    @NotBlank(message = "{val.newPassword.confirm}")
    private String confirmPassword;
}
