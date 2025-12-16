package com.bulkemail.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_logs")
@Data
@NoArgsConstructor
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String recipient;
    private String subject;

    @Column(unique = true)
    private String trackingId; // UUID for tracking pixel

    @Enumerated(EnumType.STRING)
    private EmailStatus status; // SENT, FAILED

    private boolean opened;

    private LocalDateTime sentAt;
    private LocalDateTime openedAt;
    private String errorMessage;

    public enum EmailStatus {
        SENT, FAILED
    }
}
