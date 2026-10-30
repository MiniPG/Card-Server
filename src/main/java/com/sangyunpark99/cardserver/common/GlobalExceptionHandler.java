package com.sangyunpark99.cardserver.common;

import com.sangyunpark99.cardserver.card.application.DuplicatedTransactionKeyException;
import com.sangyunpark99.cardserver.card.application.TransactionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    public record ErrorResponse(String code, String message) {}

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(TransactionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND_TRANSACTION", e.getMessage()));
    }

    @ExceptionHandler(DuplicatedTransactionKeyException.class)
    public ResponseEntity<ErrorResponse> duplicated(DuplicatedTransactionKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATED_TRANSACTION_KEY", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }
}
