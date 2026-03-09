package com.solbeg.sas.perfmgmnt.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "bot_feedback")
public class BotFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bot_feedback_seq_gen")
    @SequenceGenerator(name = "bot_feedback_seq_gen", sequenceName = "bot_feedback_seq")
    Long id;

    @Column(nullable = false, length = 4000)
    private String question;

    @Column(nullable = false, length = 10000)
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BotFeedbackType botFeedbackType;

    private String employeeEmail;

    @Generated
    @Column(updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
