package com.inventory.api.error;

import java.util.Comparator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into an {@link ApiError} body, keeping the status Spring MVC chose.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("Unexpected error"));
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return ResponseEntity.status(status).headers(headers).body(new ApiError(messageFor(ex, status)));
	}

	private String messageFor(Exception ex, HttpStatusCode status) {
		if (ex instanceof MethodArgumentNotValidException invalid) {
			return invalid.getFieldErrors()
				.stream()
				.sorted(Comparator.comparing(FieldError::getField))
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining("; "));
		}
		if (ex instanceof HttpMessageNotReadableException) {
			return "Malformed JSON request body";
		}
		if (status.value() == HttpStatus.NOT_FOUND.value()) {
			return "Resource not found";
		}
		if (ex instanceof ErrorResponse errorResponse && errorResponse.getBody().getDetail() != null) {
			return errorResponse.getBody().getDetail();
		}
		HttpStatus known = HttpStatus.resolve(status.value());
		return known != null ? known.getReasonPhrase() : "Request failed";
	}

}
