package learnifyapp.userandpreevaluation.usermanagement.exception;

import learnifyapp.userandpreevaluation.usermanagement.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("ERROR", ex.getMessage() != null ? ex.getMessage() : "An error occurred"));
    }

    @ExceptionHandler(ActiveSessionsException.class)
    public ResponseEntity<ApiErrorResponse> handle(ActiveSessionsException ex) {
        return ResponseEntity.status(409)
                .body(new ApiErrorResponse(
                        "ACTIVE_SESSIONS",
                        ex.getMessage()
                ));
    }
}
