package com.bulkemail.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecipientStatusDTO {
    private String email;
    private String name;
    private String status;
    private Boolean opened;
    private Integer openCount;
    private LocalDateTime sentAt;
    private LocalDateTime firstOpenedAt;
    private LocalDateTime lastOpenedAt;
    private String errorMessage;
}