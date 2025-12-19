package com.bulkemail.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bulkemail.dto.CampaignDashboardDTO;
import com.bulkemail.dto.CampaignReportDTO;
import com.bulkemail.dto.RecipientStatusDTO;
import com.bulkemail.service.DashboardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Get all campaigns with statistics
     * 
     * @param authentication Current authenticated user
     * @return List of campaigns with metrics
     */
    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignDashboardDTO>> getAllCampaigns(
            Authentication authentication
    ) {
        try {
            log.info("Fetching campaigns for user: {}", authentication.getName());
            
            List<CampaignDashboardDTO> campaigns = dashboardService
                .getUserCampaigns(authentication.getName());
            
            log.info("Found {} campaigns", campaigns.size());
            return ResponseEntity.ok(campaigns);
            
        } catch (IllegalArgumentException e) {
            log.error("User not found: {}", authentication.getName());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error fetching campaigns", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get detailed campaign report with statistics
     * 
     * @param campaignId Campaign ID
     * @param authentication Current authenticated user
     * @return Campaign report with stats and recipients
     */
    @GetMapping("/campaigns/{campaignId}/report")
    public ResponseEntity<CampaignReportDTO> getCampaignReport(
            @PathVariable Long campaignId,
            Authentication authentication
    ) {
        try {
            log.info("Fetching report for campaign: {} by user: {}", 
                campaignId, authentication.getName());
            
            CampaignReportDTO report = dashboardService
                .getCampaignReport(campaignId, authentication.getName());
            
            if (report == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(report);
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error fetching campaign report", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get recipient-level details with filtering
     * 
     * @param campaignId Campaign ID
     * @param status Filter by status (SENT, OPENED, FAILED, etc.)
     * @param authentication Current authenticated user
     * @return List of recipients with detailed status
     */
    @GetMapping("/campaigns/{campaignId}/recipients")
    public ResponseEntity<List<RecipientStatusDTO>> getRecipientStatus(
            @PathVariable Long campaignId,
            @RequestParam(required = false) String status, // SENT, OPENED, FAILED
            Authentication authentication
    ) {
        try {
            log.info("Fetching recipients for campaign: {} with status filter: {} by user: {}", 
                campaignId, status, authentication.getName());
            
            List<RecipientStatusDTO> recipients = dashboardService
                .getCampaignRecipients(campaignId, status, authentication.getName());
            
            log.info("Found {} recipients", recipients.size());
            return ResponseEntity.ok(recipients);
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error fetching recipients", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}