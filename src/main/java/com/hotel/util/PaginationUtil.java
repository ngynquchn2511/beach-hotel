package com.hotel.util;

import java.util.ArrayList;
import java.util.List;

// Phan trang don gian tren danh sach da co san trong bo nho (khong can sua repository/query)
// Dung chung cho cac man hinh danh sach o admin va customer.
public final class PaginationUtil {

    public static final int DEFAULT_PAGE_SIZE = 10;

    // So 0 trong danh sach so trang tra ve tu pageNumbersToShow() dai dien cho dau "..."
    public static final int ELLIPSIS = 0;

    private PaginationUtil() {
    }

    public static int totalPages(int totalItems, int pageSize) {
        return Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
    }

    public static <T> List<T> slice(List<T> all, int page, int pageSize) {
        int totalPages = totalPages(all.size(), pageSize);
        int safePage = Math.max(1, Math.min(page, totalPages));
        int from = (safePage - 1) * pageSize;
        int to = Math.min(from + pageSize, all.size());
        if (from >= to) {
            return List.of();
        }
        return all.subList(from, to);
    }

    // Danh sach so trang de hien vd: 1 2 ... 5 6 - luon giu trang dau, trang cuoi
    // va cac trang lan can trang hien tai, phan con lai thay bang dau "..."
    public static List<Integer> pageNumbersToShow(int currentPage, int totalPages) {
        List<Integer> result = new ArrayList<>();
        if (totalPages <= 7) {
            for (int i = 1; i <= totalPages; i++) {
                result.add(i);
            }
            return result;
        }

        result.add(1);
        int start = Math.max(2, currentPage - 1);
        int end = Math.min(totalPages - 1, currentPage + 1);
        if (start > 2) {
            result.add(ELLIPSIS);
        }
        for (int i = start; i <= end; i++) {
            result.add(i);
        }
        if (end < totalPages - 1) {
            result.add(ELLIPSIS);
        }
        result.add(totalPages);
        return result;
    }
}
