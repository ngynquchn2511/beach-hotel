package com.hotel.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegisterRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    private RegisterRequest validRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setFullName("Nguyen Quoc Hoan");
        r.setEmail("hoan@mail.com");
        r.setPhoneNumber("0901234567");
        r.setPassword("Abc12345");
        r.setConfirmPassword("Abc12345");
        return r;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    void blankFullName_isRejected() {
        RegisterRequest r = validRequest();
        r.setFullName("");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(r);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("fullName")));
    }

    @Test
    void invalidEmailFormat_isRejected() {
        RegisterRequest r = validRequest();
        r.setEmail("khong-hop-le");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(r);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    void invalidPhoneFormat_isRejected() {
        RegisterRequest r = validRequest();
        r.setPhoneNumber("abc123");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(r);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("phoneNumber")));
    }

    @Test
    void phoneNotStartingWithZero_isRejected() {
        RegisterRequest r = validRequest();
        r.setPhoneNumber("1901234567");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(r);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("phoneNumber")));
    }

    @Test
    void shortPassword_isRejected() {
        RegisterRequest r = validRequest();
        r.setPassword("123");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(r);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void blankConfirmPassword_isRejected() {
        RegisterRequest r = validRequest();
        r.setConfirmPassword("");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(r);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("confirmPassword")));
    }
}
