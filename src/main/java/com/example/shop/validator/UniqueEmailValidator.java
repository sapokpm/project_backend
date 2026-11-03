package com.example.shop.validator;

import com.example.shop.repository.UserRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {
    private final UserRepository users;

    public UniqueEmailValidator(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        // null/blank is @NotBlank's job
        return email == null || email.isBlank() || !users.existsByEmail(email.trim().toLowerCase());
    }
}
