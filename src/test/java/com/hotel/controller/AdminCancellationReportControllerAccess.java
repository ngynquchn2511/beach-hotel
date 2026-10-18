package com.hotel.controller;

import java.time.YearMonth;

// Cho test goi ham package-private range() cua controller
public final class AdminCancellationReportControllerAccess {

    private AdminCancellationReportControllerAccess() {
    }

    public static YearMonth[] range(YearMonth from, YearMonth to) {
        return AdminCancellationReportController.range(from, to);
    }
}
