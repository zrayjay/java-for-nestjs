package dev.javafornestjs.security;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityErrors {
    @ExceptionHandler(AuthService.Failure.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Map<String, String> authentication() { return Map.of("code", "unauthorized"); }
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Map<String, String> authorization() { return Map.of("code", "forbidden"); }
    @ExceptionHandler(AuthService.UnknownUser.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> missing() { return Map.of("code", "not_found"); }
}
