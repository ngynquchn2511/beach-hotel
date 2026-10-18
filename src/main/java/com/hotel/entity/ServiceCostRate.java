package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Ty le gia von (%) tren doanh thu tung loai dich vu (VD do an goi len phong 35%) - de tinh lai gop theo nguon thu
@Entity
@Table(name = "service_cost_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceCostRate {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", length = 20)
    private ChargeType chargeType;

    @Column(nullable = false)
    private Integer percent;
}
