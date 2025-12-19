package com.bulkemail.controller;

import com.bulkemail.dto.CampaignStatusResponse;
import com.bulkemail.dto.EmailTrackingDTO;
import com.bulkemail.dto.SendCampaignRequest;
import com.bulkemail.service.EmailSenderService;
import com.bulkemail.service.EmailTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EmailController {

    private final EmailSenderService emailSenderService;
    private final EmailTrackingService trackingService;

    private static final byte[] TRACKING_PIXEL = new byte[]{
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
        0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
        0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
        0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00,
        0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, (byte) 0x42, 0x60, (byte) 0x82
    };

    /**
     * Send bulk email campaign
     */
    @PostMapping("/email/send-campaign")
    public ResponseEntity sendCampaign(
            @Valid @ModelAttribute SendCampaignRequest request,
            Authentication authentication
    ) {
        log.info("Campaign request from: {}", authentication.getName());
        
        // Process and queue emails
        CampaignStatusResponse response = emailSenderService
            .createAndSendCampaign(request, authentication.getName());
        
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Track email open (invisible pixel)
     */
    @GetMapping(value = "/tracking/pixel/{trackingId}.png", 
                produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity trackPixel(
            @PathVariable String trackingId,
            HttpServletRequest request
    ) {
        trackingService.trackEmailOpen(trackingId, request);
        
        return ResponseEntity.ok()
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("Expires", "0")
            .body(TRACKING_PIXEL);
    }

    /**
     * Track link clicks
     */
    @GetMapping("/tracking/click/{trackingId}")
    public ResponseEntity trackClick(
            @PathVariable String trackingId,
            @RequestParam String url
    ) {
        trackingService.trackLinkClick(trackingId, url);
        return ResponseEntity.status(302)
            .header("Location", url)
            .build();
    }

    /**
     * Get tracking details for specific email
     */
    @GetMapping("/tracking/details/{trackingId}")
    public ResponseEntity getTrackingDetails(
            @PathVariable String trackingId
    ) {
        EmailTrackingDTO details = trackingService.getTrackingDetails(trackingId);
        return ResponseEntity.ok(details);
    }
}