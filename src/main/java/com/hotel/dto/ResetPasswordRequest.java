package com.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "{val.resetToken.required}")
    private String token;

    @NotBlank(message = "{val.password.required}")
    @Size(min = 6, message = "{val.password.min}")
    private String password;

    @NotBlank(message = "{val.password.confirm}")
    private String confirmPassword;
}
