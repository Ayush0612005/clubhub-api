package com.clubhub.recruitment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    public record AnswerRequest(
            @NotNull Long questionId,
            @NotBlank @Size(max = 5000) String answer) {
    }

    public record ApplyRequest(@NotNull @Size(max = 20) List<@Valid @NotNull AnswerRequest> answers) {
    }

    /** What a student sees about their own application: no internal notes, no reviewer names. */
    public record MyApplicationResponse(Long id, Long driveId, String driveTitle, ApplicationStatus status,
                                        Instant submittedAt, Instant updatedAt) {

        static MyApplicationResponse from(Application a, String driveTitle) {
            return new MyApplicationResponse(a.getId(), a.getDriveId(), driveTitle, a.getStatus(),
                    a.getSubmittedAt(), a.getUpdatedAt());
        }
    }
}
