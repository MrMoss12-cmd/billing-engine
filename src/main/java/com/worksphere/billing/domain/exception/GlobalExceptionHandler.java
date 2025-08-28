package com.billingengine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvoiceGenerationException.class)
    public ResponseEntity<Object> handleInvoiceException(InvoiceGenerationException ex) {
        return buildResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<Object> handlePaymentException(PaymentException ex) {
        return buildResponse(ex, HttpStatus.PAYMENT_REQUIRED);
    }

    @ExceptionHandler(TaxCalculationException.class)
    public ResponseEntity<Object> handleTaxException(TaxCalculationException ex) {
        return buildResponse(ex, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Object> buildResponse(Exception ex, HttpStatus status) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", Instant.now().toString());
        error.put("status", status.value());
        error.put("error", status.getReasonPhrase());
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, status);
    }
}
