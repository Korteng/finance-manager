package ru.korteng.finance_manager.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String message;
    private List<FieldErrorDetail> errors;
    private Instant timestamp = Instant.now();

    public ErrorResponse(String message) {
        this.message = message;
    }

    public ErrorResponse(String message, List<FieldErrorDetail> errors) {
        this.message = message;
        this.errors = errors;
    }

    @Data
    public static class FieldErrorDetail {
        private String field;
        private Object rejectedValue;
        private String message;
    }
}
