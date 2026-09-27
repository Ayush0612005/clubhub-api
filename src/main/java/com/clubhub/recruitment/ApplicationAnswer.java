package com.clubhub.recruitment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "application_answers")
public class ApplicationAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private Application application;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Long questionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    protected ApplicationAnswer() {
        // for JPA
    }

    ApplicationAnswer(Application application, Long questionId, String answer) {
        this.application = application;
        this.questionId = questionId;
        this.answer = answer;
    }

    public Long getQuestionId() { return questionId; }
    public String getAnswer() { return answer; }
}
