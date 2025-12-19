package com.bulkemail.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class CampaignDashboardDTO {
    private Long id;
    private String campaignName;
    private String subject;
    private String status;
    private Integer totalRecipients;
    private Integer sentCount;
    private Integer deliveredCount;
    private Integer openedCount;
    private Integer failedCount;
    private Double openRate;
    private LocalDateTime scheduledAt;
    private LocalDateTime completedAt;
}
