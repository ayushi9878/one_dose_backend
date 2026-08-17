package com.careflow.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Standard error envelope returned by every failing endpoint.")
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requestId,
        Map<String, String> fieldErrors,
        List<String> details) {

    public static ApiError of(int status, String error, String message, String path, String requestId) {
        return new ApiError(Instant.now(), status, error, message, path, requestId, null, null);
    }

    public static ApiError validation(String path, String requestId, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), 400, "Bad Request",
                "Validation failed for one or more fields.", path, requestId, fieldErrors, null);
    }
}
