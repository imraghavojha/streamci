package com.yourname.streamci.streamci.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.fasterxml.jackson.core.JsonParseException;
import java.util.Map;
import java.util.HashMap;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleBadJson(HttpMessageNotReadableException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Malformed JSON payload");
        error.put("message", "Unable to parse request body");
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(JsonParseException.class)
    public ResponseEntity<Map<String, String>> handleJsonParseError(JsonParseException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Invalid JSON format");
        error.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }
}