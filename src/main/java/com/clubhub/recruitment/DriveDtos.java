package com.clubhub.recruitment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class DriveDtos {

    private DriveDtos() {
    }

    public record QuestionRequest(
            @NotBlank @Size(max = 500) String prompt,
            boolean required) {
    }

    public record CreateDriveRequest(
            @NotBlank @Size(max = 150) String title,
            @Size(max = 5000) String description,
            @Future Instant closesAt,
            @NotEmpty @Size(max = 20) List<@Valid @NotNull QuestionRequest> questions) {
    }

    public record ChangeDriveStatusRequest(@NotNull DriveStatus status) {
    }

    public record QuestionResponse(Long id, int sortOrder, String prompt, boolean required) {

        static QuestionResponse from(DriveQuestion q) {
            return new QuestionResponse(q.getId(), q.getSortOrder(), q.getPrompt(), q.isRequired());
        }
    }

    public record DriveSummary(Long id, String title, DriveStatus status, Instant closesAt, Instant createdAt) {

        static DriveSummary from(RecruitmentDrive d) {
            return new DriveSummary(d.getId(), d.getTitle(), d.getStatus(), d.getClosesAt(), d.getCreatedAt());
        }
    }

    public record DriveResponse(Long id, String title, String description, DriveStatus status, Instant closesAt,
                                Instant createdAt, List<QuestionResponse> questions) {

        static DriveResponse from(RecruitmentDrive d) {
            return new DriveResponse(d.getId(), d.getTitle(), d.getDescription(), d.getStatus(), d.getClosesAt(),
                    d.getCreatedAt(), d.getQuestions().stream().map(QuestionResponse::from).toList());
        }
    }
}
