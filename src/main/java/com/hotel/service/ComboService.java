package com.hotel.service;

import com.hotel.dto.ComboRequest;
import com.hotel.entity.Combo;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ComboRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class ComboService {

    private final ComboRepository comboRepository;
    private final FileStorageService fileStorageService;

    public ComboService(ComboRepository comboRepository, FileStorageService fileStorageService) {
        this.comboRepository = comboRepository;
        this.fileStorageService = fileStorageService;
    }

    public List<Combo> findAll() {
        return comboRepository.findAll();
    }

    public List<Combo> findActive() {
        return comboRepository.findByActiveTrue();
    }

    public Combo findById(Long id) {
        return comboRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy combo"));
    }

    @Transactional
    public Combo create(ComboRequest request, MultipartFile imageFile) {
        String imageUrl = fileStorageService.store(imageFile, "combos");
        Combo combo = Combo.builder()
                .name(request.getName())
                .description(request.getDescription())
                .nameEn(blankToNull(request.getNameEn()))
                .descriptionEn(blankToNull(request.getDescriptionEn()))
                .price(request.getPrice())
                .maxGuests(request.getMaxGuests())
                .imageUrl(imageUrl)
                .active(true)
                .build();
        return comboRepository.save(combo);
    }

    @Transactional
    public Combo update(Long id, ComboRequest request, MultipartFile imageFile) {
        Combo combo = findById(id);
        combo.setName(request.getName());
        combo.setDescription(request.getDescription());
        combo.setNameEn(blankToNull(request.getNameEn()));
        combo.setDescriptionEn(blankToNull(request.getDescriptionEn()));
        combo.setPrice(request.getPrice());
        combo.setMaxGuests(request.getMaxGuests());

        if (imageFile != null && !imageFile.isEmpty()) {
            String oldImageUrl = combo.getImageUrl();
            combo.setImageUrl(fileStorageService.store(imageFile, "combos"));
            fileStorageService.delete(oldImageUrl);
        }

        return comboRepository.save(combo);
    }

    @Transactional
    public void toggleActive(Long id) {
        Combo combo = findById(id);
        combo.setActive(!combo.isActive());
        comboRepository.save(combo);
    }

    @Transactional
    public void delete(Long id) {
        Combo combo = findById(id);
        fileStorageService.delete(combo.getImageUrl());
        comboRepository.deleteById(id);
    }

    // O ban tieng Anh de trong -> luu null (trang khach tu dung ban tieng Viet)
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
