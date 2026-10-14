package com.hotel.service;

import com.hotel.entity.Airport;
import com.hotel.entity.TransferRate;
import com.hotel.entity.VehicleType;
import com.hotel.exception.BusinessException;
import com.hotel.repository.TransferRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

// Bang gia dua don san bay (1 chieu) theo san bay x loai xe
@Service
public class TransferRateService {

    private static final BigDecimal MAX_PRICE = BigDecimal.valueOf(50_000_000);

    // Gia mac dinh khi chua co bang gia (lan dau chay) - admin sua lai o trang "Giá đưa đón"
    private static final Map<Airport, long[]> DEFAULT_PRICES = Map.of(
            Airport.VAN_DON, new long[]{650_000, 800_000, 1_300_000},
            Airport.NOI_BAI, new long[]{1_500_000, 1_800_000, 2_800_000},
            Airport.CAT_BI, new long[]{850_000, 1_000_000, 1_600_000});

    private final TransferRateRepository rateRepository;

    public TransferRateService(TransferRateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    // Tao bang gia mac dinh cho cac o con thieu (goi luc khoi dong)
    @Transactional
    public void ensureDefaults() {
        for (Airport airport : Airport.values()) {
            long[] prices = DEFAULT_PRICES.get(airport);
            for (VehicleType vehicle : VehicleType.values()) {
                if (rateRepository.findByAirportAndVehicleType(airport, vehicle).isEmpty()) {
                    rateRepository.save(TransferRate.builder().airport(airport).vehicleType(vehicle)
                            .price(BigDecimal.valueOf(prices[vehicle.ordinal()])).build());
                }
            }
        }
    }

    // Bang gia dang [san bay][loai xe] -> gia, dung cho trang khach / trang admin / chatbot
    public Map<Airport, Map<VehicleType, BigDecimal>> priceTable() {
        Map<Airport, Map<VehicleType, BigDecimal>> table = new EnumMap<>(Airport.class);
        for (TransferRate rate : rateRepository.findAll()) {
            table.computeIfAbsent(rate.getAirport(), a -> new EnumMap<>(VehicleType.class))
                    .put(rate.getVehicleType(), rate.getPrice());
        }
        return table;
    }

    public BigDecimal priceOf(Airport airport, VehicleType vehicle) {
        return rateRepository.findByAirportAndVehicleType(airport, vehicle)
                .map(TransferRate::getPrice)
                .orElseThrow(() -> BusinessException.of("err.svc.noRate"));
    }

    @Transactional
    public void updatePrices(Map<String, String> form) {
        for (Airport airport : Airport.values()) {
            for (VehicleType vehicle : VehicleType.values()) {
                String raw = form.get("price_" + airport + "_" + vehicle);
                if (raw == null || raw.isBlank()) {
                    throw new BusinessException("Vui lòng nhập đủ giá cho tất cả sân bay và loại xe");
                }
                BigDecimal price;
                try {
                    price = new BigDecimal(raw.replace(".", "").replace(",", "").trim());
                } catch (NumberFormatException ex) {
                    throw new BusinessException("Giá không hợp lệ: " + raw);
                }
                if (price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0) {
                    throw new BusinessException("Giá đưa đón phải lớn hơn 0 và không quá 50.000.000đ");
                }
                TransferRate rate = rateRepository.findByAirportAndVehicleType(airport, vehicle)
                        .orElseGet(() -> TransferRate.builder().airport(airport).vehicleType(vehicle).build());
                rate.setPrice(price.setScale(0, RoundingMode.HALF_UP));
                rateRepository.save(rate);
            }
        }
    }
}
