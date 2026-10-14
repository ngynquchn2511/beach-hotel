package com.hotel.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Dia chi goc cua website (VD https://songxanhresort.vn) cho the Open Graph / canonical / sitemap.
// Uu tien app.base-url (APP_BASE_URL); chua dat thi lay theo request hien tai
@ControllerAdvice
public class SeoModelAdvice {

    private final String baseUrl;

    public SeoModelAdvice(@Value("${app.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @ModelAttribute("siteBaseUrl")
    public String siteBaseUrl() {
        return resolve(baseUrl);
    }

    static String resolve(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim().replaceAll("/+$", "");
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
