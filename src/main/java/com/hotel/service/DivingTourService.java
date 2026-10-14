package com.hotel.service;

import com.hotel.dto.DivingTourRequest;
import com.hotel.entity.DivingTour;
import com.hotel.exception.BusinessException;
import com.hotel.repository.DivingTourRepository;
import com.hotel.repository.TourBookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// Danh muc tour lan bien (admin them/sua/an/xoa)
@Service
public class DivingTourService {

    private final DivingTourRepository tourRepository;
    private final TourBookingRepository tourBookingRepository;
    private final FileStorageService fileStorageService;

    public DivingTourService(DivingTourRepository tourRepository, TourBookingRepository tourBookingRepository,
                             FileStorageService fileStorageService) {
        this.tourRepository = tourRepository;
        this.tourBookingRepository = tourBookingRepository;
        this.fileStorageService = fileStorageService;
    }

    public List<DivingTour> findAll() {
        return tourRepository.findAllByOrderByIdAsc();
    }

    public List<DivingTour> findActive() {
        return tourRepository.findByActiveTrueOrderByPricePerPersonAsc();
    }

    public DivingTour findById(Long id) {
        return tourRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tour"));
    }

    @Transactional
    public DivingTour create(DivingTourRequest request, MultipartFile imageFile) {
        DivingTour tour = new DivingTour();
        apply(tour, request);
        tour.setImageUrl(fileStorageService.store(imageFile, "tours"));
        tour.setActive(true);
        return tourRepository.save(tour);
    }

    @Transactional
    public DivingTour update(Long id, DivingTourRequest request, MultipartFile imageFile) {
        DivingTour tour = findById(id);
        apply(tour, request);
        if (imageFile != null && !imageFile.isEmpty()) {
            String oldImageUrl = tour.getImageUrl();
            tour.setImageUrl(fileStorageService.store(imageFile, "tours"));
            fileStorageService.delete(oldImageUrl);
        }
        return tourRepository.save(tour);
    }

    @Transactional
    public void toggleActive(Long id) {
        DivingTour tour = findById(id);
        tour.setActive(!tour.isActive());
        tourRepository.save(tour);
    }

    // Tour da co khach dat thi khong xoa (mat lich su don) - chi cho an di
    @Transactional
    public void delete(Long id) {
        DivingTour tour = findById(id);
        if (tourBookingRepository.existsByTourId(id)) {
            throw new BusinessException("Tour đã có khách đặt nên không xóa được, hãy bấm Ẩn để ngừng bán");
        }
        fileStorageService.delete(tour.getImageUrl());
        tourRepository.delete(tour);
    }

    public DivingTourRequest toRequest(DivingTour tour) {
        DivingTourRequest request = new DivingTourRequest();
        request.setName(tour.getName());
        request.setDescription(tour.getDescription());
        request.setNameEn(tour.getNameEn());
        request.setDescriptionEn(tour.getDescriptionEn());
        request.setPricePerPerson(tour.getPricePerPerson());
        request.setDepartureTime(tour.getDepartureTime());
        request.setDurationHours(tour.getDurationHours());
        request.setMeetingPoint(tour.getMeetingPoint());
        request.setMaxParticipants(tour.getMaxParticipants());
        request.setMinAge(tour.getMinAge());
        return request;
    }

    private void apply(DivingTour tour, DivingTourRequest request) {
        tour.setName(request.getName().trim());
        tour.setDescription(blankToNull(request.getDescription()));
        tour.setNameEn(blankToNull(request.getNameEn()));
        tour.setDescriptionEn(blankToNull(request.getDescriptionEn()));
        tour.setPricePerPerson(request.getPricePerPerson());
        tour.setDepartureTime(request.getDepartureTime());
        tour.setDurationHours(request.getDurationHours());
        tour.setMeetingPoint(blankToNull(request.getMeetingPoint()));
        tour.setMaxParticipants(request.getMaxParticipants());
        tour.setMinAge(request.getMinAge());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
