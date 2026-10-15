package com.hotel.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;

// Mot trang cua danh sach + du lieu ve thanh phan trang (fragments/pagination :: pager).
// Link sang trang khac giu nguyen moi tham so loc dang co tren URL (ngay, loai phong, sap xep...), chi doi tham so trang.
// param: ten tham so trang tren URL - trang co 2 danh sach thi moi danh sach dung 1 ten rieng (vd tourPage, transferPage)
public record Pager<T>(List<T> items, int currentPage, int pageSize, int totalPages, long totalItems,
                       List<Integer> pageNumbers, String param, String baseUrl) {

    public static <T> Pager<T> of(List<T> all, int page, int pageSize) {
        return of(all, page, pageSize, "page");
    }

    public static <T> Pager<T> of(List<T> all, int page, int pageSize, String param) {
        int totalPages = PaginationUtil.totalPages(all.size(), pageSize);
        int current = Math.max(1, Math.min(page, totalPages));
        return new Pager<>(PaginationUtil.slice(all, current, pageSize), current, pageSize, totalPages, all.size(),
                PaginationUtil.pageNumbersToShow(current, totalPages), param, currentUrlWithout(param));
    }

    public boolean hasPages() {
        return totalPages > 1;
    }

    // Link toi trang p, vd /customer/rooms?checkIn=...&guests=2&page=3
    public String url(int p) {
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + param + "=" + p;
    }

    // So phan tu dung truoc trang nay - cong vao so thu tu de STT lien tuc qua cac trang
    public int offset() {
        return (currentPage - 1) * pageSize;
    }

    private static String currentUrlWithout(String param) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return "";
        }
        HttpServletRequest request = attrs.getRequest();
        StringJoiner query = new StringJoiner("&");
        request.getParameterMap().forEach((name, values) -> {
            if (!name.equals(param)) {
                for (String value : values) {
                    query.add(encode(name) + "=" + encode(value));
                }
            }
        });
        return request.getRequestURI() + (query.length() > 0 ? "?" + query : "");
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
