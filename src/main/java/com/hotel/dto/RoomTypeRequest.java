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
public class RoomTypeRequest {

    @NotBlank(message = "Tên loại phòng không được để trống")
    private String name;

    @NotNull(message = "Giá cơ bản không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá cơ bản phải lớn hơn 0")
    private BigDecimal basePrice;

    @NotNull(message = "Sức chứa không được để trống")
    @Min(value = 1, message = "Sức chứa tối thiểu là 1 người")
    private Integer maxGuests;

    private BigDecimal area;

    private String description;

    private String amenities;

    // Ban tieng Anh (tuy chon) - bo trong thi trang khach tieng Anh hien ban tieng Viet
    @Size(max = 100, message = "Tên tiếng Anh tối đa 100 ký tự")
    private String nameEn;

    @Size(max = 1000, message = "Mô tả tiếng Anh tối đa 1000 ký tự")
    private String descriptionEn;

    @Size(max = 500, message = "Tiện ích tiếng Anh tối đa 500 ký tự")
    private String amenitiesEn;
}
