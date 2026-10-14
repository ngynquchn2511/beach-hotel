package com.hotel.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ComboRequest {

    @NotBlank(message = "Tên combo không được để trống")
    private String name;

    private String description;

    // Ban tieng Anh (tuy chon) - bo trong thi trang khach tieng Anh hien ban tieng Viet
    @Size(max = 150, message = "Tên tiếng Anh tối đa 150 ký tự")
    private String nameEn;

    @Size(max = 1000, message = "Mô tả tiếng Anh tối đa 1000 ký tự")
    private String descriptionEn;

    @NotNull(message = "Giá combo không được để trống")
    @DecimalMin(value = "0.0", message = "Giá combo không được âm")
    private BigDecimal price;

    @Min(value = 1, message = "Số người áp dụng phải lớn hơn 0")
    private Integer maxGuests;
}
