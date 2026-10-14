package com.hotel.service;

import com.hotel.dto.RoomRequest;
import com.hotel.entity.Room;
import com.hotel.entity.RoomImage;
import com.hotel.entity.RoomStatus;
import com.hotel.entity.RoomType;
import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomImageRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RoomService {

    private static final int MAX_IMAGES_PER_ROOM = 6;

    private final RoomRepository roomRepository;
    private final RoomTypeService roomTypeService;
    private final BookingRepository bookingRepository;
    private final FileStorageService fileStorageService;
    private final RoomImageRepository roomImageRepository;

    public RoomService(RoomRepository roomRepository, RoomTypeService roomTypeService,
                        BookingRepository bookingRepository, FileStorageService fileStorageService,
                        RoomImageRepository roomImageRepository) {
        this.roomRepository = roomRepository;
        this.roomTypeService = roomTypeService;
        this.bookingRepository = bookingRepository;
        this.fileStorageService = fileStorageService;
        this.roomImageRepository = roomImageRepository;
    }

    public List<Room> findAll() {
        return roomRepository.findAll();
    }

    public Room findById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> BusinessException.of("err.roomNotFound"));
    }

    public List<RoomImage> findImages(Long roomId) {
        return roomImageRepository.findByRoomIdOrderById(roomId);
    }

    // BR-02: ngay tra phong phai lon hon ngay nhan phong
    // Bo sung: khong cho phep dat phong voi ngay nhan phong trong qua khu
    public List<Room> searchAvailableRooms(LocalDate checkIn, LocalDate checkOut, Integer guests, Long roomTypeId) {
        if (checkIn == null || checkOut == null) {
            throw BusinessException.of("err.datesRequired");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw BusinessException.of("err.checkInPast");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw BusinessException.of("err.checkOutAfterCheckIn");
        }
        return roomRepository.findAvailableRooms(checkIn, checkOut, roomTypeId, guests);
    }

    // Dung khi xem chi tiet phong de xac nhan phong con trong hay khong (BR-01, BR-03)
    public boolean isAvailableForDates(Long roomId, LocalDate checkIn, LocalDate checkOut) {
        Room room = findById(roomId);
        if (room.getStatus() == RoomStatus.MAINTENANCE) {
            return false;
        }
        return !bookingRepository.existsOverlappingBooking(roomId, checkIn, checkOut);
    }

    // Cac ngay da co khach o duoc hien mau xam trong lich dat phong.
    // Ngay tra phong khong bi khoa vi khach moi van co the nhan phong trong ngay do.
    public List<LocalDate> findUnavailableDates(Long roomId) {
        return bookingRepository.findByRoomIdAndStatusIn(roomId, List.of(
                        BookingStatus.PENDING,
                        BookingStatus.CONFIRMED,
                        BookingStatus.CHECKED_IN))
                .stream()
                .flatMap(booking -> booking.getCheckInDate().datesUntil(booking.getCheckOutDate()))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    // Cac ngay chi co don CHO XAC NHAN giu cho (chua co don xac nhan) - lich hien mau rieng de khach biet
    public List<LocalDate> findPendingOnlyDates(Long roomId) {
        List<Booking> active = bookingRepository.findByRoomIdAndStatusIn(roomId, List.of(
                BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN));
        java.util.Set<LocalDate> confirmed = active.stream()
                .filter(b -> b.getStatus() != BookingStatus.PENDING)
                .flatMap(b -> b.getCheckInDate().datesUntil(b.getCheckOutDate()))
                .collect(java.util.stream.Collectors.toSet());
        return active.stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING)
                .flatMap(b -> b.getCheckInDate().datesUntil(b.getCheckOutDate()))
                .filter(d -> !confirmed.contains(d))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    @Transactional
    public Room create(RoomRequest request, List<MultipartFile> imageFiles) {
        // Business rule: so phong khong duoc trung
        if (roomRepository.existsByRoomNumber(request.getRoomNumber())) {
            throw new BusinessException("Số phòng này đã tồn tại");
        }

        List<MultipartFile> validFiles = filterValidFiles(imageFiles);
        if (validFiles.size() > MAX_IMAGES_PER_ROOM) {
            throw new BusinessException("Chỉ được tải tối đa " + MAX_IMAGES_PER_ROOM + " ảnh cho mỗi phòng");
        }

        RoomType roomType = roomTypeService.findById(request.getRoomTypeId());

        Room room = Room.builder()
                .roomNumber(request.getRoomNumber())
                .roomType(roomType)
                .price(request.getPrice())
                .description(request.getDescription())
                .descriptionEn(blankToNull(request.getDescriptionEn()))
                .status(RoomStatus.AVAILABLE)
                .build();

        room = roomRepository.save(room);

        saveImages(room, validFiles);

        return room;
    }

    @Transactional
    public Room update(Long id, RoomRequest request, List<MultipartFile> imageFiles) {
        Room room = findById(id);

        // Neu doi so phong thi kiem tra trung voi phong khac
        if (!room.getRoomNumber().equals(request.getRoomNumber())
                && roomRepository.existsByRoomNumber(request.getRoomNumber())) {
            throw new BusinessException("Số phòng này đã tồn tại");
        }

        RoomType roomType = roomTypeService.findById(request.getRoomTypeId());

        room.setRoomNumber(request.getRoomNumber());
        room.setRoomType(roomType);
        room.setPrice(request.getPrice());
        room.setDescription(request.getDescription());
        room.setDescriptionEn(blankToNull(request.getDescriptionEn()));
        room = roomRepository.save(room);

        List<MultipartFile> validFiles = filterValidFiles(imageFiles);
        if (!validFiles.isEmpty()) {
            long existingCount = roomImageRepository.countByRoomId(id);
            if (existingCount + validFiles.size() > MAX_IMAGES_PER_ROOM) {
                throw new BusinessException("Phòng này chỉ được tối đa " + MAX_IMAGES_PER_ROOM
                        + " ảnh (hiện có " + existingCount + " ảnh, hãy xóa bớt ảnh cũ nếu muốn thêm ảnh mới)");
            }
            saveImages(room, validFiles);
        }

        return room;
    }

    @Transactional
    public void deleteImage(Long roomId, Long imageId) {
        RoomImage image = roomImageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy ảnh"));
        if (!image.getRoom().getId().equals(roomId)) {
            throw new BusinessException("Ảnh này không thuộc phòng đã chọn");
        }
        fileStorageService.delete(image.getImageUrl());
        roomImageRepository.delete(image);
    }

    // Thay the mot anh cu the bang anh moi, giu nguyen vi tri/thu tu trong danh sach
    @Transactional
    public void replaceImage(Long roomId, Long imageId, MultipartFile newImageFile) {
        if (newImageFile == null || newImageFile.isEmpty()) {
            throw new BusinessException("Vui lòng chọn file ảnh để thay thế");
        }
        RoomImage image = roomImageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy ảnh"));
        if (!image.getRoom().getId().equals(roomId)) {
            throw new BusinessException("Ảnh này không thuộc phòng đã chọn");
        }

        String oldImageUrl = image.getImageUrl();
        String newUrl = fileStorageService.store(newImageFile, "rooms");
        image.setImageUrl(newUrl);
        roomImageRepository.save(image);
        fileStorageService.delete(oldImageUrl);
    }

    @Transactional
    public void changeStatus(Long id, RoomStatus status) {
        Room room = findById(id);
        room.setStatus(status);
        roomRepository.save(room);
    }

    // Nhan vien/admin xac nhan da don dep xong sau khi khach checkout: NOT_READY -> AVAILABLE
    @Transactional
    public void markReady(Long id) {
        Room room = findById(id);
        if (room.getStatus() != RoomStatus.NOT_READY) {
            throw new BusinessException("Phòng này hiện không ở trạng thái chờ dọn dẹp");
        }
        room.setStatus(RoomStatus.AVAILABLE);
        roomRepository.save(room);
    }

    // Dung cho KPI tren dashboard
    public long countByStatus(RoomStatus status) {
        return roomRepository.findAll().stream().filter(r -> r.getStatus() == status).count();
    }

    @Transactional
    public void delete(Long id) {
        // Rang buoc du lieu: khong cho xoa phong neu da co booking gan voi phong nay
        boolean hasBookings = bookingRepository.findAll().stream()
                .anyMatch(booking -> booking.getRoom().getId().equals(id));
        if (hasBookings) {
            throw new BusinessException("Không thể xóa phòng này vì đã có lịch sử đặt phòng liên quan");
        }
        List<RoomImage> images = roomImageRepository.findByRoomIdOrderById(id);
        images.forEach(img -> fileStorageService.delete(img.getImageUrl()));
        roomImageRepository.deleteByRoomId(id);
        roomRepository.deleteById(id);
    }

    private List<MultipartFile> filterValidFiles(List<MultipartFile> imageFiles) {
        List<MultipartFile> result = new ArrayList<>();
        if (imageFiles == null) {
            return result;
        }
        for (MultipartFile f : imageFiles) {
            if (f != null && !f.isEmpty()) {
                result.add(f);
            }
        }
        return result;
    }

    private void saveImages(Room room, List<MultipartFile> files) {
        for (MultipartFile file : files) {
            String url = fileStorageService.store(file, "rooms");
            RoomImage image = RoomImage.builder()
                    .room(room)
                    .imageUrl(url)
                    .build();
            roomImageRepository.save(image);
        }
    }

    // O ban tieng Anh de trong -> luu null (trang khach tu dung ban tieng Viet)
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
