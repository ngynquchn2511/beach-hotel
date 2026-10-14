package com.hotel.tc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Gan cho phuong thuc test: mo ta "Cac buoc thuc hien" cua test case
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TcSteps {
    String value();
}
