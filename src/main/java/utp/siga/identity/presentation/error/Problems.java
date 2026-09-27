package utp.siga.identity.presentation.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import utp.siga.identity.domain.exception.IdentityException;
import utp.siga.identity.presentation.rest.CorrelationFilter;

@RestControllerAdvice
public class Problems {
  public static Map<String, Object> body(int status, String code, String detail, String path) {
    return Map.of(
        "type",
        "about:blank",
        "title",
        HttpStatus.valueOf(status).getReasonPhrase(),
        "status",
        status,
        "code",
        code,
        "detail",
        detail,
        "instance",
        path,
        "correlationId",
        CorrelationFilter.current().toString());
  }

  public static void write(HttpServletRequest req, HttpServletResponse res, int status, String code)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/problem+json");
    new ObjectMapper()
        .writeValue(
            res.getOutputStream(),
            body(status, code, "Solicitud no autorizada", req.getRequestURI()));
  }

  private ResponseEntity<?> response(
      int status, String code, String detail, HttpServletRequest req) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body(status, code, detail, req.getRequestURI()));
  }

  @ExceptionHandler(IdentityException.class)
  ResponseEntity<?> domain(IdentityException e, HttpServletRequest req) {
    return response(e.status, e.code, e.getMessage(), req);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    jakarta.validation.ConstraintViolationException.class
  })
  ResponseEntity<?> invalid(Exception e, HttpServletRequest req) {
    return response(400, "VALIDATION_ERROR", "Revisa los campos de la solicitud", req);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> duplicate(Exception e, HttpServletRequest req) {
    return response(
        409, "DATA_CONFLICT", "El usuario, correo, rol o asignación ya existe o no es válido", req);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> forbidden(Exception e, HttpServletRequest req) {
    return response(403, "AUTH_FORBIDDEN", "Permiso insuficiente", req);
  }

  @ExceptionHandler({
    RedisConnectionFailureException.class,
    org.springframework.dao.DataAccessResourceFailureException.class
  })
  ResponseEntity<?> unavailable(Exception e, HttpServletRequest req) {
    return response(503, "DEPENDENCY_UNAVAILABLE", "Dependencia temporalmente no disponible", req);
  }
}
