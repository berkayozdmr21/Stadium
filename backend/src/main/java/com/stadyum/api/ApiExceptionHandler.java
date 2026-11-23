package com.stadyum.api;

import com.stadyum.domain.DomainException;
import com.stadyum.domain.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Bütün hataları RFC 9457 "problem details" biçiminde döndürür. Her yanıtta makinece okunur bir
 * {@code code} alanı bulunur; arayüz metne değil bu koda göre davranır.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e) {
        return problem(statusOf(e.code()), e.code().name(), e.getMessage());
    }

    /** Veritabanı kilidi zamanında alınamadı: istemci aynı isteği yeniden deneyebilir. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    ProblemDetail busy(ConcurrencyFailureException e) {
        log.debug("Kilit alınamadı: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, "BUSY", "Şu an yoğunluk var. Lütfen tekrar dene.");
    }

    /** Beklenmeyen hata: ayrıntı günlüğe yazılır, istemciye sızdırılmaz. */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Beklenmeyen hata", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Beklenmeyen bir hata oluştu.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION", "Girilen bilgilerde hata var.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, badRequest(), headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, badRequest(), headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Spring'in kendi ürettiği yanıtlara (404, 405, 415...) da {@code code} alanı eklenir. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            problem.setProperty("code", genericCode(status));
        }
        return response;
    }

    private static ProblemDetail badRequest() {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "İstek anlaşılamadı.");
    }

    private static String genericCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            default -> status.is4xxClientError() ? "BAD_REQUEST" : "INTERNAL";
        };
    }

    private static HttpStatus statusOf(ErrorCode code) {
        return switch (code) {
            case EVENT_NOT_FOUND, SEAT_NOT_FOUND, BLOCK_NOT_FOUND, BOOKING_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SEAT_UNAVAILABLE, EMPTY_HOLD, SALES_CLOSED -> HttpStatus.CONFLICT;
            case HOLD_EXPIRED -> HttpStatus.GONE;
            case MAX_SEATS_EXCEEDED -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
