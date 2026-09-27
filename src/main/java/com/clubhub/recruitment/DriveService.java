package com.clubhub.recruitment;

import com.clubhub.common.ConflictException;
import com.clubhub.common.NotFoundException;
import com.clubhub.recruitment.DriveDtos.CreateDriveRequest;
import com.clubhub.recruitment.DriveDtos.DriveResponse;
import com.clubhub.recruitment.DriveDtos.DriveSummary;
import com.clubhub.recruitment.DriveDtos.QuestionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Club-side management of recruitment drives. Runs inside the caller's club schema (TenantContext
 * is bound by TenantFilter), so every query here only ever sees this club's drives.
 */
@Service
public class DriveService {

    private final DriveRepository drives;

    public DriveService(DriveRepository drives) {
        this.drives = drives;
    }

    @Transactional
    public DriveResponse create(CreateDriveRequest request, UUID createdBy) {
        RecruitmentDrive drive = new RecruitmentDrive(
                request.title().strip(), request.description(), request.closesAt(), createdBy);
        for (QuestionRequest q : request.questions()) {
            drive.addQuestion(q.prompt().strip(), q.required());
        }
        return DriveResponse.from(drives.save(drive));
    }

    /** Plain members only see drives that have been published; drafts are for the core team. */
    @Transactional(readOnly = true)
    public List<DriveSummary> list(boolean includeDrafts) {
        return drives.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> includeDrafts || d.getStatus() != DriveStatus.DRAFT)
                .map(DriveSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DriveResponse get(Long id, boolean includeDrafts) {
        RecruitmentDrive drive = drives.findWithQuestionsById(id)
                .filter(d -> includeDrafts || d.getStatus() != DriveStatus.DRAFT)
                .orElseThrow(() -> new NotFoundException("Drive not found"));
        return DriveResponse.from(drive);
    }

    @Transactional
    public DriveResponse changeStatus(Long id, DriveStatus target) {
        RecruitmentDrive drive = drives.findWithQuestionsById(id)
                .orElseThrow(() -> new NotFoundException("Drive not found"));
        if (target == DriveStatus.OPEN && drive.getClosesAt() != null && !Instant.now().isBefore(drive.getClosesAt())) {
            throw new ConflictException("The drive's deadline has already passed");
        }
        try {
            drive.changeStatus(target);
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        return DriveResponse.from(drive);
    }
}
