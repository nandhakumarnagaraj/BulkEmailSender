package com.bulkemail.service;

import com.bulkemail.dto.CampaignDashboardDTO;
import com.bulkemail.dto.CampaignReportDTO;
import com.bulkemail.dto.RecipientStatusDTO;
import com.bulkemail.entity.EmailCampaign;
import com.bulkemail.entity.EmailLog;
import com.bulkemail.entity.User;
import com.bulkemail.repository.EmailCampaignRepository;
import com.bulkemail.repository.EmailLogRepository;
import com.bulkemail.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final EmailCampaignRepository campaignRepository;
    private final EmailLogRepository logRepository;
    private final UserRepository userRepository;

    /**
     * Get all campaigns for a user with statistics
     */
    public List<CampaignDashboardDTO> getUserCampaigns(String username) {
        log.info("Fetching campaigns for user: {}", username);
        
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> {
                    log.error("User not found: {}", username);
                    return new IllegalArgumentException("User not found");
                });

        return campaignRepository.findByUserId(user.getId())
                .stream()
                .map(this::convertToDashboardDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get detailed campaign report with statistics
     */
    public CampaignReportDTO getCampaignReport(Long campaignId, String username) {
        log.info("Fetching report for campaign: {} by user: {}", campaignId, username);
        
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> {
                    log.error("User not found: {}", username);
                    return new IllegalArgumentException("User not found");
                });

        EmailCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> {
                    log.error("Campaign not found: {}", campaignId);
                    return new IllegalArgumentException("Campaign not found");
                });

        // Verify campaign belongs to user
        if (!campaign.getUser().getId().equals(user.getId())) {
            log.warn("Unauthorized access attempt - User: {}, Campaign: {}", username, campaignId);
            throw new IllegalArgumentException("Unauthorized access to campaign");
        }

        return buildCampaignReport(campaign);
    }

    /**
     * Get recipient-level details with optional status filtering
     */
    public List<RecipientStatusDTO> getCampaignRecipients(Long campaignId, String status, String username) {
        log.info("Fetching recipients for campaign: {} with status: {} by user: {}", 
            campaignId, status, username);
        
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> {
                    log.error("User not found: {}", username);
                    return new IllegalArgumentException("User not found");
                });

        EmailCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> {
                    log.error("Campaign not found: {}", campaignId);
                    return new IllegalArgumentException("Campaign not found");
                });

        // Verify campaign belongs to user
        if (!campaign.getUser().getId().equals(user.getId())) {
            log.warn("Unauthorized access attempt - User: {}, Campaign: {}", username, campaignId);
            throw new IllegalArgumentException("Unauthorized access to campaign");
        }

        List<EmailLog> logs = campaign.getEmailLogs();

        if (status != null && !status.isEmpty()) {
            try {
                EmailLog.EmailStatus emailStatus = EmailLog.EmailStatus.valueOf(status.toUpperCase());
                logs = logs.stream()
                        .filter(log -> log.getStatus() == emailStatus)
                        .collect(Collectors.toList());
                log.debug("Filtered {} logs by status: {}", logs.size(), status);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status filter: {}", status);
                // Return all logs if status is invalid
            }
        }

        return logs.stream()
                .map(this::convertToRecipientDTO)
                .collect(Collectors.toList());
    }

    private CampaignDashboardDTO convertToDashboardDTO(EmailCampaign campaign) {
        double openRate = campaign.getTotalRecipients() > 0
                ? (campaign.getOpenedCount() * 100.0) / campaign.getTotalRecipients()
                : 0.0;

        return CampaignDashboardDTO.builder()
                .id(campaign.getId())
                .campaignName(campaign.getCampaignName())
                .subject(campaign.getSubject())
                .status(campaign.getStatus().toString())
                .totalRecipients(campaign.getTotalRecipients())
                .sentCount(campaign.getSentCount())
                .deliveredCount(campaign.getDeliveredCount())
                .openedCount(campaign.getOpenedCount())
                .failedCount(campaign.getFailedCount())
                .openRate(openRate)
                .scheduledAt(campaign.getScheduledAt())
                .completedAt(campaign.getCompletedAt())
                .build();
    }

    private CampaignReportDTO buildCampaignReport(EmailCampaign campaign) {
        int total = campaign.getTotalRecipients();
        int opened = campaign.getOpenedCount();
        int clicked = (int) campaign.getEmailLogs().stream()
                .filter(EmailLog::getClicked)
                .count();
        int sent = campaign.getSentCount();
        int delivered = campaign.getDeliveredCount();
        int failed = campaign.getFailedCount();
        int bounced = 0;

        double openRate = total > 0 ? (opened * 100.0) / total : 0.0;
        double clickRate = total > 0 ? (clicked * 100.0) / total : 0.0;
        double bounceRate = total > 0 ? (bounced * 100.0) / total : 0.0;

        List<String> recipients = campaign.getEmailLogs().stream()
                .map(EmailLog::getRecipientEmail)
                .collect(Collectors.toList());

        return CampaignReportDTO.builder()
                .campaignId(campaign.getId())
                .campaignName(campaign.getCampaignName())
                .stats(CampaignReportDTO.CampaignStats.builder()
                        .total(total)
                        .sent(sent)
                        .delivered(delivered)
                        .opened(opened)
                        .clicked(clicked)
                        .failed(failed)
                        .bounced(bounced)
                        .openRate(openRate)
                        .clickRate(clickRate)
                        .bounceRate(bounceRate)
                        .build())
                .recipients(recipients)
                .build();
    }

    private RecipientStatusDTO convertToRecipientDTO(EmailLog log) {
        return RecipientStatusDTO.builder()
                .email(log.getRecipientEmail())
                .name(log.getRecipientName())
                .status(log.getStatus().toString())
                .opened(log.getOpened())
                .openCount(log.getOpenCount())
                .sentAt(log.getSentAt())
                .firstOpenedAt(log.getFirstOpenedAt())
                .lastOpenedAt(log.getLastOpenedAt())
                .errorMessage(log.getErrorMessage())
                .build();
    }
}
