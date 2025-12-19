package com.bulkemail.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_logs", indexes = {
    @Index(name = "idx_tracking_id", columnList = "trackingId"),
    @Index(name = "idx_campaign_id", columnList = "campaign_id"),
    @Index(name = "idx_recipient_email", columnList = "recipientEmail")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private EmailCampaign campaign;

    @Column(nullable = false)
    private String recipientEmail;

    private String recipientName;

    @Column(unique = true, nullable = false)
    private String trackingId; // UUID for tracking

    @Column(unique = true)
    private String messageId; // Email server message ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EmailStatus status = EmailStatus.QUEUED;

    @Builder.Default
    private Boolean opened = false;

    @Builder.Default
    private Integer openCount = 0;

    @Builder.Default
    private Boolean clicked = false;

    @Builder.Default
    private Integer clickCount = 0;

    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime firstOpenedAt;
    private LocalDateTime lastOpenedAt;
    private LocalDateTime firstClickedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private String userAgent; // Browser/Email client info
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String smtpResponse;

    @Builder.Default
    private Integer retryCount = 0;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum EmailStatus {
        QUEUED,       // In queue waiting to be sent
        SENDING,      // Currently being sent
        SENT,         // Successfully sent to SMTP server
        DELIVERED,    // Confirmed delivered (if tracking available)
        BOUNCED,      // Hard bounce
        SOFT_BOUNCED, // Temporary failure
        FAILED,       // Failed to send
        OPENED,       // Recipient opened email
        CLICKED       // Recipient clicked link
    }
}