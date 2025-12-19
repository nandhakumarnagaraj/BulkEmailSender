package com.bulkemail.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CampaignReportDTO {
    private Long campaignId;
    private String campaignName;
    private CampaignStats stats;
    private List<String> recipients;
    
    @Data
    @Builder
    public static class CampaignStats {
        private Integer total;
        private Integer sent;
        private Integer delivered;
        private Integer opened;
        private Integer clicked;
        private Integer failed;
        private Integer bounced;
        private Double openRate;
        private Double clickRate;
        private Double bounceRate;
    }
}
