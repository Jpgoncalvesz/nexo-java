package br.com.nexo.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> business(ApiException error, HttpServletRequest request) { return response(error.status(), error.code(), error.getMessage(), request); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<?> invalid(Exception error, HttpServletRequest request) { return response(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", "Verifique os campos e os tipos dos dados enviados.", request); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> integrity(Exception error, HttpServletRequest request) { return response(HttpStatus.CONFLICT, "CONFLITO_DADOS", "O registro já existe ou viola uma regra do banco de dados.", request); }
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<?> concurrent(Exception error, HttpServletRequest request) { return response(HttpStatus.CONFLICT, "OPERACAO_CONCORRENTE", "O registro está ocupado. Tente novamente.", request); }
    private ResponseEntity<?> response(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(Map.of("codigo", code, "mensagem", message, "caminho", request.getRequestURI(), "instante", Instant.now()));
    }
}
