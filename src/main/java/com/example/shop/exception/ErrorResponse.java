package com.example.shop.exception;

import java.time.LocalDateTime;
import java.util.Map;

/** The one error body shape every endpoint returns. */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> details) {
}
