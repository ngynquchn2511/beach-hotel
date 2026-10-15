package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Ca truc quay le tan: mo ca (tien mat dau ca) -> dong ca (dem tien mat, nop ket, ghi chu ban giao cho ca sau)
@Entity
@Table(name = "cash_shifts", indexes = @Index(name = "idx_shift_status", columnList = "status, opened_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashShift {

    public enum Status {
        OPEN, CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by", nullable = false)
    private User openedBy;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    // Tien mat trong ngan keo khi nhan ca
    @Column(name = "opening_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingCash;

    // Ca truoc de lai bao nhieu (dem - nop ket) - de thay lech khi nhan ca
    @Column(name = "previous_left_cash", precision = 12, scale = 2)
    private BigDecimal previousLeftCash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // Chot luc dong ca: tong thu (tru hoan) theo phuong thuc trong ca
    @Column(name = "cash_in", precision = 12, scale = 2)
    private BigDecimal cashIn;

    @Column(name = "transfer_in", precision = 12, scale = 2)
    private BigDecimal transferIn;

    @Column(name = "online_in", precision = 12, scale = 2)
    private BigDecimal onlineIn;

    // Tien mat dem thuc te luc dong ca
    @Column(name = "counted_cash", precision = 12, scale = 2)
    private BigDecimal countedCash;

    // Tien mat nop ket / ngan hang luc dong ca (phan con lai de cho ca sau)
    @Column(name = "cash_to_safe", precision = 12, scale = 2)
    private BigDecimal cashToSafe;

    @Column(name = "handover_note", length = 1000)
    private String handoverNote;

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    // Tien mat dang le phai co = dau ca + thu tien mat
    public BigDecimal getExpectedCash() {
        return cashIn == null ? null : openingCash.add(cashIn);
    }

    // Dem - du kien (am = thieu tien)
    public BigDecimal getCashDifference() {
        return countedCash == null || cashIn == null ? null : countedCash.subtract(getExpectedCash());
    }

    // De lai ngan keo cho ca sau
    public BigDecimal getLeftCash() {
        return countedCash == null ? null : countedCash.subtract(cashToSafe == null ? BigDecimal.ZERO : cashToSafe);
    }

    // Nhan ca lech so voi ca truoc de lai
    public BigDecimal getOpeningDifference() {
        return previousLeftCash == null ? null : openingCash.subtract(previousLeftCash);
    }
}
