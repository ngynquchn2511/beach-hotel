package com.hotel.tc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Gan cho lop test: cap do kiem thu (UT/IT/ST) va ten module de TcRecorder ghi vao bang tong hop test case
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TcSuite {
    String level();

    String module();
}
