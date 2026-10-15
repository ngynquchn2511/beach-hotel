package com.hotel.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PagerTest {

    private final List<Integer> items = IntStream.rangeClosed(1, 25).boxed().toList();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void request(String uri, String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                request.addParameter(kv[0], java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void slicesAndClampsPage() {
        Pager<Integer> p = Pager.of(items, 3, 10);
        assertThat(p.items()).containsExactly(21, 22, 23, 24, 25);
        assertThat(p.totalPages()).isEqualTo(3);
        assertThat(p.offset()).isEqualTo(20);
        assertThat(p.hasPages()).isTrue();

        assertThat(Pager.of(items, 99, 10).currentPage()).isEqualTo(3);
        assertThat(Pager.of(items, -5, 10).currentPage()).isEqualTo(1);
        assertThat(Pager.of(List.of(), 1, 10).hasPages()).isFalse();
    }

    @Test
    void url_keepsOtherParamsAndReplacesPage() {
        request("/customer/rooms", "checkIn=2026-11-01&guests=2&page=2&price=r2");

        Pager<Integer> p = Pager.of(items, 2, 10);

        assertThat(p.url(3)).isEqualTo("/customer/rooms?checkIn=2026-11-01&guests=2&price=r2&page=3");
    }

    @Test
    void url_withoutQueryAndCustomParam() {
        request("/admin/service-orders", "tourPage=2&transferPage=4");
        assertThat(Pager.of(items, 1, 10, "tourPage").url(2)).isEqualTo("/admin/service-orders?transferPage=4&tourPage=2");

        request("/rooms", null);
        assertThat(Pager.of(items, 1, 10).url(2)).isEqualTo("/rooms?page=2");
    }

    @Test
    void url_keepsEncodedValues() {
        request("/admin/customers", "q=Nguy%E1%BB%85n%20A%26B&page=1");

        assertThat(Pager.of(items, 1, 10).url(2)).isEqualTo("/admin/customers?q=Nguy%E1%BB%85n+A%26B&page=2");
    }
}
