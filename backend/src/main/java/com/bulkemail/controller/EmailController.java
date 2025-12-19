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

import org.springframework.http.HttpStatus;
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

    // Transparent 1x1 pixel PNG bytes
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
     * 
     * @param request Campaign details with recipients and attachments
     * @param authentication Current authenticated user
     * @return Campaign status response with ID and email count
     */
    @PostMapping("/email/send-campaign")
    public ResponseEntity<CampaignStatusResponse> sendCampaign(
            @Valid @ModelAttribute SendCampaignRequest request,
            Authentication authentication
    ) {
        try {
            log.info("Campaign request from user: {}", authentication.getName());

            // Validate request
            if (request.getRecipients() == null || request.getRecipients().isEmpty()) {
                log.warn("Campaign request with no recipients from: {}", authentication.getName());
                return ResponseEntity.badRequest().build();
            }

            if (request.getName() == null || request.getName().trim().isEmpty()) {
                log.warn("Campaign request with no name from: {}", authentication.getName());
                return ResponseEntity.badRequest().build();
            }

            if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
                log.warn("Campaign request with no subject from: {}", authentication.getName());
                return ResponseEntity.badRequest().build();
            }

            if (request.getBody() == null || request.getBody().trim().isEmpty()) {
                log.warn("Campaign request with no body from: {}", authentication.getName());
                return ResponseEntity.badRequest().build();
            }

            // Process and queue emails
            CampaignStatusResponse response = emailSenderService
                .createAndSendCampaign(request, authentication.getName());

            log.info("Campaign created with ID: {} for {} recipients", 
                response.getCampaignId(), response.getTotalEmails());

            return ResponseEntity.accepted().body(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid campaign request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            log.error("Error processing campaign request", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Track email open via invisible pixel
     * 
     * @param trackingId Unique tracking ID for this email
     * @param request HTTP request (contains IP, user agent)
     * @return 1x1 PNG pixel image
     */
    @GetMapping(value = "/email/track/{trackingId}.png", 
                produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> trackPixel(
            @PathVariable String trackingId,
            HttpServletRequest request
    ) {
        try {
            if (trackingId == null || trackingId.trim().isEmpty()) {
                log.warn("Tracking pixel request with empty tracking ID");
                return ResponseEntity.badRequest().build();
            }

            log.debug("Tracking pixel view for tracking ID: {}", trackingId);
            trackingService.trackEmailOpen(trackingId, request);

            return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .contentType(MediaType.IMAGE_PNG)
                .body(TRACKING_PIXEL);

        } catch (Exception e) {
            log.error("Error tracking email open for tracking ID: {}", trackingId, e);
            return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .body(TRACKING_PIXEL); // Still return pixel to avoid breaking email
        }
    }

    /**
     * Track link clicks and redirect to destination URL
     * 
     * @param trackingId Unique tracking ID for this email
     * @param url Destination URL to redirect to
     * @return 302 redirect to destination URL
     */
    @GetMapping("/email/track/click/{trackingId}")
    public ResponseEntity<?> trackClick(
            @PathVariable String trackingId,
            @RequestParam(name = "url", required = false) String url
    ) {
        try {
            if (trackingId == null || trackingId.trim().isEmpty()) {
                log.warn("Click tracking request with empty tracking ID");
                return ResponseEntity.badRequest().build();
            }

            if (url == null || url.trim().isEmpty()) {
                log.warn("Click tracking request with empty URL from tracking ID: {}", trackingId);
                return ResponseEntity.badRequest().build();
            }

            log.debug("Click tracked for tracking ID: {} to URL: {}", trackingId, url);
            trackingService.trackLinkClick(trackingId, url);

            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", url)
                .build();

        } catch (Exception e) {
            log.error("Error tracking click for tracking ID: {}", trackingId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get detailed tracking information for a specific email
     * 
     * @param trackingId Unique tracking ID
     * @return Email tracking details (opens, clicks, user agent, etc.)
     */
    @GetMapping("/email/track/details/{trackingId}")
    public ResponseEntity<EmailTrackingDTO> getTrackingDetails(
            @PathVariable String trackingId
    ) {
        try {
            if (trackingId == null || trackingId.trim().isEmpty()) {
                log.warn("Tracking details request with empty tracking ID");
                return ResponseEntity.badRequest().build();
            }

            log.debug("Fetching tracking details for tracking ID: {}", trackingId);
            EmailTrackingDTO details = trackingService.getTrackingDetails(trackingId);

            if (details == null) {
                log.warn("No tracking data found for tracking ID: {}", trackingId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(details);

        } catch (Exception e) {
            log.error("Error fetching tracking details for tracking ID: {}", trackingId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}