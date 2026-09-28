package com.clubhub.common;

import com.clubhub.auth.AuthExceptions;
import com.clubhub.event.TicketService;
import com.clubhub.plan.PlanExceptions;
import com.clubhub.tenant.TenantAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
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

    @ExceptionHandler(AuthExceptions.EmailNotAllowedException.class)
    ProblemDetail handleEmailNotAllowed(AuthExceptions.EmailNotAllowedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(AuthExceptions.InvalidCredentialsException.class)
    ProblemDetail handleBadCredentials(AuthExceptions.InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AuthExceptions.InvalidRefreshTokenException.class)
    ProblemDetail handleBadRefreshToken(AuthExceptions.InvalidRefreshTokenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AuthExceptions.ClubAccessDeniedException.class)
    ProblemDetail handleClubAccessDenied(AuthExceptions.ClubAccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** @PreAuthorize denials (e.g. a MEMBER calling a CORE-only endpoint): consistent problem+json body. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Your role does not allow this action");
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleStateConflict(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(TicketService.InvalidTicketException.class)
    ProblemDetail handleInvalidTicket(TicketService.InvalidTicketException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** 409 + a machine-readable code so the frontend can show an "upgrade" prompt. */
    @ExceptionHandler(PlanExceptions.PlanLimitExceededException.class)
    ProblemDetail handlePlanLimit(PlanExceptions.PlanLimitExceededException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setProperty("code", "PLAN_LIMIT");
        return problem;
    }

    @ExceptionHandler(PlanExceptions.FeatureNotAvailableException.class)
    ProblemDetail handleFeatureNotAvailable(PlanExceptions.FeatureNotAvailableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problem.setProperty("code", "FEATURE_NOT_IN_PLAN");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
