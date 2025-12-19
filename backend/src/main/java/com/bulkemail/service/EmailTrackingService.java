package com.bulkemail.service;

import com.bulkemail.dto.EmailTrackingDTO;
import com.bulkemail.entity.EmailCampaign;
import com.bulkemail.entity.EmailLog;
import com.bulkemail.entity.EmailLog.EmailStatus;
import com.bulkemail.repository.EmailLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTrackingService {

    private final EmailLogRepository emailLogRepository;

    /**
     * Track email open via pixel
     */
    @Transactional
    public void trackEmailOpen(String trackingId, HttpServletRequest request) {
        emailLogRepository.findByTrackingId(trackingId).ifPresent(emailLog -> {
            
            // First time opened
            if (!emailLog.getOpened()) {
                emailLog.setOpened(true);
                emailLog.setFirstOpenedAt(LocalDateTime.now());
                emailLog.setStatus(EmailStatus.OPENED);
                
                log.info("Email first opened: {} by {}", 
                    trackingId, emailLog.getRecipientEmail());
            }

            // Track every open
            emailLog.setOpenCount(emailLog.getOpenCount() + 1);
            emailLog.setLastOpenedAt(LocalDateTime.now());
            
            // Capture user agent and IP
            emailLog.setUserAgent(request.getHeader("User-Agent"));
            emailLog.setIpAddress(getClientIpAddress(request));

            emailLogRepository.save(emailLog);

            // Update campaign statistics
            updateCampaignStats(emailLog.getCampaign());
        });
    }

    /**
     * Track link clicks
     */
    @Transactional
    public void trackLinkClick(String trackingId, String linkUrl) {
        emailLogRepository.findByTrackingId(trackingId).ifPresent(emailLog -> {
            
            if (!emailLog.getClicked()) {
                emailLog.setClicked(true);
                emailLog.setFirstClickedAt(LocalDateTime.now());
                emailLog.setStatus(EmailStatus.CLICKED);
            }

            emailLog.setClickCount(emailLog.getClickCount() + 1);
            emailLogRepository.save(emailLog);

            log.info("Link clicked: {} by {} - URL: {}", 
                trackingId, emailLog.getRecipientEmail(), linkUrl);
        });
    }

    /**
     * Get detailed tracking info
     */
    public EmailTrackingDTO getTrackingDetails(String trackingId) {
        return emailLogRepository.findByTrackingId(trackingId)
            .map(this::convertToDTO)
            .orElse(null);
    }

    private void updateCampaignStats(EmailCampaign campaign) {
        if (campaign != null) {
            long openedCount = emailLogRepository.countByCampaignAndOpened(campaign, true);
            campaign.setOpenedCount((int) openedCount);
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private EmailTrackingDTO convertToDTO(EmailLog log) {
        return EmailTrackingDTO.builder()
            .recipientEmail(log.getRecipientEmail())
            .status(log.getStatus().toString())
            .sent(log.getSentAt() != null)
            .opened(log.getOpened())
            .openCount(log.getOpenCount())
            .clicked(log.getClicked())
            .clickCount(log.getClickCount())
            .firstOpenedAt(log.getFirstOpenedAt())
            .lastOpenedAt(log.getLastOpenedAt())
            .userAgent(log.getUserAgent())
            .build();
    }
}