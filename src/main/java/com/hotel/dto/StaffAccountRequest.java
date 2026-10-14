package com.hotel.dto;

import com.hotel.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaffAccountRequest {

    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại phải có 10 số và bắt đầu bằng 0")
    private String phoneNumber;

    // Chi STAFF hoac ADMIN - service tu choi CUSTOMER
    @NotNull(message = "Vui lòng chọn vai trò")
    private UserRole role;

    // Bat buoc khi tao moi; khi sua thi de trong nghia la giu nguyen mat khau cu
    private String password;
}
