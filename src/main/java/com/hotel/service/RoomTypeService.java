package com.hotel.service;

import com.hotel.dto.RoomTypeRequest;
import com.hotel.entity.RoomType;
import com.hotel.exception.BusinessException;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;

    public RoomTypeService(RoomTypeRepository roomTypeRepository, RoomRepository roomRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
    }

    public List<RoomType> findAll() {
        return roomTypeRepository.findAll();
    }

    public RoomType findById(Long id) {
        return roomTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy loại phòng"));
    }

    @Transactional
    public RoomType create(RoomTypeRequest request) {
        RoomType roomType = RoomType.builder()
                .name(request.getName())
                .basePrice(request.getBasePrice())
                .maxGuests(request.getMaxGuests())
                .area(request.getArea())
                .description(request.getDescription())
                .amenities(request.getAmenities())
                .nameEn(blankToNull(request.getNameEn()))
                .descriptionEn(blankToNull(request.getDescriptionEn()))
                .amenitiesEn(blankToNull(request.getAmenitiesEn()))
                .build();
        return roomTypeRepository.save(roomType);
    }

    @Transactional
    public RoomType update(Long id, RoomTypeRequest request) {
        RoomType roomType = findById(id);
        roomType.setName(request.getName());
        roomType.setBasePrice(request.getBasePrice());
        roomType.setMaxGuests(request.getMaxGuests());
        roomType.setArea(request.getArea());
        roomType.setDescription(request.getDescription());
        roomType.setAmenities(request.getAmenities());
        roomType.setNameEn(blankToNull(request.getNameEn()));
        roomType.setDescriptionEn(blankToNull(request.getDescriptionEn()));
        roomType.setAmenitiesEn(blankToNull(request.getAmenitiesEn()));
        return roomTypeRepository.save(roomType);
    }

    @Transactional
    public void delete(Long id) {
        // Rang buoc du lieu: khong cho xoa loai phong neu van con phong thuoc loai nay
        boolean stillInUse = roomRepository.findAll().stream()
                .anyMatch(room -> room.getRoomType().getId().equals(id));
        if (stillInUse) {
            throw new BusinessException("Không thể xóa loại phòng này vì vẫn còn phòng thuộc loại phòng đó");
        }
        roomTypeRepository.deleteById(id);
    }

    // O ban tieng Anh de trong -> luu null (trang khach tu dung ban tieng Viet)
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
