package com.clubhub.common;

import com.clubhub.auth.AuthExceptions;
import com.clubhub.tenant.TenantAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One error format for the whole API: RFC 9457 Problem Details (application/problem+json).
 * The base class already converts validation failures (@Valid) into 400 problem responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(TenantAlreadyExistsException.class)
    ProblemDetail handleConflict(TenantAlreadyExistsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AuthExceptions.EmailAlreadyUsedException.class)
    ProblemDetail handleEmailTaken(AuthExceptions.EmailAlreadyUsedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AuthExceptions.InvalidCredentialsException.class)
    ProblemDetail handleBadCredentials(AuthExceptions.InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
