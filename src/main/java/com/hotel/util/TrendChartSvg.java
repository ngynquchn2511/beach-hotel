package com.hotel.util;

import java.math.BigDecimal;
import java.util.List;

// Bieu do cot SVG ve san tren server (khong can thu vien JS): 1 truc doc duy nhat, luoi mo, moi cot co tooltip (<title>).
// Mau theo bang mau tham chieu da kiem tra mu mau: chuoi 1 xanh duong, chuoi 2 cam; lai/lo: duong xanh / am do.
public final class TrendChartSvg {

    public static final String BLUE = "#2a78d6";
    public static final String ORANGE = "#eb6834";
    public static final String RED = "#e34948";
    private static final String GRID = "#e1e0d9";
    private static final String AXIS = "#c3c2b7";
    private static final String MUTED = "#898781";

    private static final int W = 720;
    private static final int H = 220;
    private static final int LEFT = 56;
    private static final int RIGHT = 8;
    private static final int TOP = 12;
    private static final int BOTTOM = 28;

    private TrendChartSvg() {
    }

    // Hai chuoi canh nhau moi thang (VD doanh thu / chi phi)
    public static String groupedBars(List<String> labels, List<BigDecimal> a, String nameA, List<BigDecimal> b, String nameB, String aria) {
        double max = Math.max(1, Math.max(max(a), max(b)));
        double nice = niceMax(max);
        StringBuilder sb = open(aria);
        grid(sb, 0, nice);
        int n = labels.size();
        double slot = (W - LEFT - RIGHT) / (double) n;
        double bw = Math.min(18, (slot - 10) / 2);
        for (int i = 0; i < n; i++) {
            double x0 = LEFT + i * slot + (slot - 2 * bw - 2) / 2;
            bar(sb, x0, bw, a.get(i).doubleValue(), 0, nice, BLUE, labels.get(i) + " · " + nameA + ": " + Money.format(a.get(i)) + " VND");
            bar(sb, x0 + bw + 2, bw, b.get(i).doubleValue(), 0, nice, ORANGE, labels.get(i) + " · " + nameB + ": " + Money.format(b.get(i)) + " VND");
            label(sb, LEFT + i * slot + slot / 2, labels.get(i));
        }
        return sb.append("</svg>").toString();
    }

    // 1 chuoi co the am (lai / lo): cot tu duong 0, duong xanh, am do
    public static String signedBars(List<String> labels, List<BigDecimal> v, String name, String aria) {
        double hi = Math.max(0, max(v));
        double lo = Math.min(0, min(v));
        double span = Math.max(1, Math.max(hi, -lo));
        double top = hi > 0 ? niceMax(hi) : 0;
        double bottom = lo < 0 ? -niceMax(-lo) : 0;
        if (top == 0 && bottom == 0) {
            top = niceMax(span);
        }
        StringBuilder sb = open(aria);
        grid(sb, bottom, top);
        int n = labels.size();
        double slot = (W - LEFT - RIGHT) / (double) n;
        double bw = Math.min(26, slot - 12);
        for (int i = 0; i < n; i++) {
            double val = v.get(i).doubleValue();
            double x0 = LEFT + i * slot + (slot - bw) / 2;
            bar(sb, x0, bw, val, bottom, top, val < 0 ? RED : BLUE, labels.get(i) + " · " + name + ": " + Money.format(v.get(i)) + " VND");
            label(sb, LEFT + i * slot + slot / 2, labels.get(i));
        }
        return sb.append("</svg>").toString();
    }

    private static StringBuilder open(String aria) {
        return new StringBuilder("<svg viewBox=\"0 0 ").append(W).append(' ').append(H)
                .append("\" width=\"100%\" role=\"img\" aria-label=\"").append(escape(aria))
                .append("\" style=\"font-family:inherit;font-size:11px\">");
    }

    private static double y(double val, double bottom, double top) {
        double plot = H - TOP - BOTTOM;
        return TOP + (top - val) / (top - bottom) * plot;
    }

    private static void grid(StringBuilder sb, double bottom, double top) {
        int steps = 4;
        for (int i = 0; i <= steps; i++) {
            double val = bottom + (top - bottom) * i / steps;
            double yy = y(val, bottom, top);
            boolean zero = Math.abs(val) < 1e-6;
            sb.append("<line x1=\"").append(LEFT).append("\" x2=\"").append(W - RIGHT).append("\" y1=\"").append(fmt(yy))
                    .append("\" y2=\"").append(fmt(yy)).append("\" stroke=\"").append(zero ? AXIS : GRID).append("\" stroke-width=\"1\"/>");
            sb.append("<text x=\"").append(LEFT - 6).append("\" y=\"").append(fmt(yy + 4)).append("\" text-anchor=\"end\" fill=\"")
                    .append(MUTED).append("\">").append(compact(val)).append("</text>");
        }
    }

    // Cot bo tron 4px o dau du lieu, neo vao duong 0; vung hover cao hon cot de de tro chuot
    private static void bar(StringBuilder sb, double x, double w, double val, double bottom, double top, String color, String tip) {
        double y0 = y(0, bottom, top);
        double y1 = y(val, bottom, top);
        double yTop = Math.min(y0, y1);
        double h = Math.max(1, Math.abs(y0 - y1));
        double r = Math.min(4, Math.min(w / 2, h));
        sb.append("<g><title>").append(escape(tip)).append("</title>");
        sb.append("<rect x=\"").append(fmt(x - 2)).append("\" y=\"").append(TOP).append("\" width=\"").append(fmt(w + 4))
                .append("\" height=\"").append(H - TOP - BOTTOM).append("\" fill=\"transparent\"/>");
        // Bo tron dau xa duong 0 bang path
        boolean up = val >= 0;
        double x2 = x + w;
        String d = up
                ? "M" + fmt(x) + "," + fmt(y0) + " V" + fmt(yTop + r) + " Q" + fmt(x) + "," + fmt(yTop) + " " + fmt(x + r) + "," + fmt(yTop)
                + " H" + fmt(x2 - r) + " Q" + fmt(x2) + "," + fmt(yTop) + " " + fmt(x2) + "," + fmt(yTop + r) + " V" + fmt(y0) + " Z"
                : "M" + fmt(x) + "," + fmt(y0) + " V" + fmt(y0 + h - r) + " Q" + fmt(x) + "," + fmt(y0 + h) + " " + fmt(x + r) + "," + fmt(y0 + h)
                + " H" + fmt(x2 - r) + " Q" + fmt(x2) + "," + fmt(y0 + h) + " " + fmt(x2) + "," + fmt(y0 + h - r) + " V" + fmt(y0) + " Z";
        sb.append("<path d=\"").append(d).append("\" fill=\"").append(color).append("\"/></g>");
    }

    private static void label(StringBuilder sb, double cx, String text) {
        sb.append("<text x=\"").append(fmt(cx)).append("\" y=\"").append(H - 10).append("\" text-anchor=\"middle\" fill=\"")
                .append(MUTED).append("\">").append(escape(text)).append("</text>");
    }

    // Lam tron truc len 1 / 2 / 2.5 / 5 x 10^n
    static double niceMax(double v) {
        double exp = Math.pow(10, Math.floor(Math.log10(v)));
        for (double m : new double[]{1, 2, 2.5, 5, 10}) {
            if (m * exp >= v) {
                return m * exp;
            }
        }
        return 10 * exp;
    }

    // 1.2 tr / 350 k
    static String compact(double v) {
        double a = Math.abs(v);
        String s;
        if (a >= 1_000_000_000) {
            s = trim(v / 1_000_000_000) + " tỷ";
        } else if (a >= 1_000_000) {
            s = trim(v / 1_000_000) + " tr";
        } else if (a >= 1_000) {
            s = trim(v / 1_000) + " k";
        } else {
            s = trim(v);
        }
        return s;
    }

    private static String trim(double v) {
        return (Math.abs(v - Math.rint(v)) < 0.05) ? String.valueOf((long) Math.rint(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static double max(List<BigDecimal> v) {
        return v.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
    }

    private static double min(List<BigDecimal> v) {
        return v.stream().mapToDouble(BigDecimal::doubleValue).min().orElse(0);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
