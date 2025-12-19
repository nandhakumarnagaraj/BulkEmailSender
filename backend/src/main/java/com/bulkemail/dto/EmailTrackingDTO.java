package com.bulkemail.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTrackingDTO {
    private String recipientEmail;
    private String status;
    private boolean sent;
    private boolean opened;
    private int openCount;
    private boolean clicked;
    private int clickCount;
    private LocalDateTime firstOpenedAt;
    private LocalDateTime lastOpenedAt;
    private String userAgent;
}
