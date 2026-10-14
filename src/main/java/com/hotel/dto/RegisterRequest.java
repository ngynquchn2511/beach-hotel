package com.hotel.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "{val.fullName.required}")
    private String fullName;

    @NotBlank(message = "{val.email.required}")
    @Email(message = "{val.email.invalid}")
    private String email;

    @NotBlank(message = "{val.phone.required}")
    @Pattern(regexp = "^0[0-9]{9}$", message = "{val.phone.pattern}")
    private String phoneNumber;

    @NotBlank(message = "{val.password.required}")
    @Size(min = 6, message = "{val.password.min}")
    private String password;

    @NotBlank(message = "{val.password.confirm}")
    private String confirmPassword;
}
