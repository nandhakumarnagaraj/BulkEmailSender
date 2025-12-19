package com.bulkemail.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bulkemail.dto.CampaignReportDTO;
import com.bulkemail.service.DashboardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Get all campaigns with statistics
     */
    @GetMapping("/campaigns")
    public ResponseEntity<List> getAllCampaigns(
            Authentication authentication
    ) {
        List campaigns = dashboardService
            .getUserCampaigns(authentication.getName());
        return ResponseEntity.ok(campaigns);
    }

    /**
     * Get detailed campaign report
     */
    @GetMapping("/campaigns/{campaignId}/report")
    public ResponseEntity getCampaignReport(
            @PathVariable Long campaignId,
            Authentication authentication
    ) {
        CampaignReportDTO report = dashboardService
            .getCampaignReport(campaignId, authentication.getName());
        return ResponseEntity.ok(report);
    }

    /**
     * Get recipient-level details
     */
    @GetMapping("/campaigns/{campaignId}/recipients")
    public ResponseEntity<List> getRecipientStatus(
            @PathVariable Long campaignId,
            @RequestParam(required = false) String status, // SENT, OPENED, FAILED
            Authentication authentication
    ) {
        List recipients = dashboardService
            .getCampaignRecipients(campaignId, status, authentication.getName());
        return ResponseEntity.ok(recipients);
    }
}