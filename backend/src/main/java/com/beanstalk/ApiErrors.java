package com.beanstalk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/** Every API failure is returned as {"error": "..."} JSON so the frontend can always show it. */
@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooBig(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(Map.of("error", "Photo is too large (max 12 MB)."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> other(Exception e) {
        log.error("Unhandled API error", e);
        return ResponseEntity.status(500).body(Map.of("error", "Server error: " + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : " - " + e.getMessage())));
    }
}
