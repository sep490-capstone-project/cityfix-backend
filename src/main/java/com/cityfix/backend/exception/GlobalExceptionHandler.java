package com.cityfix.backend.exception;

import com.cityfix.backend.model.dto.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    public ResponseEntity<BaseResponse<Void>> handleAppException(AppException ex) {
        ErrorCode code = ex.getErrorCode();

        return ResponseEntity
                .status(code.getStatus())
                .body(BaseResponse.error(code.name(), code.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(ErrorCode.VALIDATION_ERROR.name(), message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<Void>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex) {

        String message = "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(ErrorCode.VALIDATION_ERROR.name(), message));
    }

    // ─── Missing required request param ───────────────────────────────────────

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<Void>> handleMissingParam(
            MissingServletRequestParameterException ex) {

        String message = "Required parameter '" + ex.getParameterName() + "' is missing.";

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(ErrorCode.VALIDATION_ERROR.name(), message));
    }

    // ─── Invalid enum in JSON body ────────────────────────────────────────────

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Void>> handleNotReadableException(
            HttpMessageNotReadableException ex) {

        String message = ex.getMessage() != null && ex.getMessage().contains("not one of the values accepted")
                ? "Invalid value: " + ex.getMessage().split("`")[1]
                : "Invalid or malformed request body.";

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(ErrorCode.VALIDATION_ERROR.name(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception occurred: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BaseResponse.error("INTERNAL_SERVER_ERROR", "An unexpected error occurred"));
    }
}
