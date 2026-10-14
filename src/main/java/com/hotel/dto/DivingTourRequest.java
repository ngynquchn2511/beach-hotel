package com.hotel.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
public class DivingTourRequest {

    @NotBlank(message = "Tên tour không được để trống")
    @Size(max = 150, message = "Tên tour tối đa 150 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
    private String description;

    @Size(max = 150, message = "Tên tiếng Anh tối đa 150 ký tự")
    private String nameEn;

    @Size(max = 1000, message = "Mô tả tiếng Anh tối đa 1000 ký tự")
    private String descriptionEn;

    @NotNull(message = "Giá mỗi người không được để trống")
    @DecimalMin(value = "1000", message = "Giá mỗi người tối thiểu 1.000đ")
    private BigDecimal pricePerPerson;

    @NotNull(message = "Giờ khởi hành không được để trống")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime departureTime;

    @NotNull(message = "Thời lượng không được để trống")
    @Min(value = 1, message = "Thời lượng tối thiểu 1 tiếng")
    @Max(value = 24, message = "Thời lượng tối đa 24 tiếng")
    private Integer durationHours;

    @Size(max = 200, message = "Điểm hẹn tối đa 200 ký tự")
    private String meetingPoint;

    @NotNull(message = "Số chỗ mỗi chuyến không được để trống")
    @Min(value = 1, message = "Số chỗ mỗi chuyến phải lớn hơn 0")
    @Max(value = 200, message = "Số chỗ mỗi chuyến tối đa 200")
    private Integer maxParticipants;

    @Min(value = 1, message = "Độ tuổi tối thiểu phải lớn hơn 0")
    @Max(value = 99, message = "Độ tuổi tối thiểu không hợp lệ")
    private Integer minAge;
}
