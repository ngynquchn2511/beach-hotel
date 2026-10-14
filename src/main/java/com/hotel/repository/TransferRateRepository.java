package com.hotel.repository;

import com.hotel.entity.Airport;
import com.hotel.entity.TransferRate;
import com.hotel.entity.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRateRepository extends JpaRepository<TransferRate, Long> {

    Optional<TransferRate> findByAirportAndVehicleType(Airport airport, VehicleType vehicleType);
}
