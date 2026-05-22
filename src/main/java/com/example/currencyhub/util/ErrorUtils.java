package com.example.currencyhub.util;

import com.example.currencyhub.common.exception.ValidationError;
import com.example.currencyhub.common.exception.ValidationException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.validation.BindingResult;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorUtils {

    public static void validate(BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            List<ValidationError> errors = mapErrors(bindingResult);
            throw new ValidationException(errors);
        }
    }

    private static List<ValidationError> mapErrors(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(e -> new ValidationError(e.getField(), e.getDefaultMessage()))
                .toList();
    }
}
