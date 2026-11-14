package com.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

// Bo dem so thu tu don theo ngay tao, dung de sinh ma don "MMdd" + 4 chu so (BookingCodeGenerator)
@Entity
@Table(name = "booking_code_counters")
@Getter
@Setter
@NoArgsConstructor
public class BookingCodeCounter {

    @Id
    @Column(name = "code_date")
    private LocalDate codeDate;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;
}
