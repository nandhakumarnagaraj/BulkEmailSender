package com.bulkemail.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.bulkemail.service.IEmailService;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/email")
@RequiredArgsConstructor
public class BulkEmailController {

    private final IEmailService emailService;

    // Transparent 1x1 pixel bytes (PNG)
    private static final byte[] PIXEL_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
            0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
            0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, (byte) 0x42,
            0x60, (byte) 0x82
    };

    @PostMapping(value = "/send-bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> sendBulkEmail(
            @RequestParam("recipients") List<String> recipients,
            @RequestParam("subject") String subject,
            @RequestParam("body") String body,
            @RequestParam(value = "attachments", required = false) List<MultipartFile> attachments) {
        emailService.sendBulkEmail(recipients, subject, body, attachments);
        return ResponseEntity.ok("Bulk email process started for " + recipients.size() + " recipients.");
    }

    @GetMapping(value = "/track/{trackingId}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] trackEmail(@PathVariable String trackingId) {
        emailService.trackEmailOpen(trackingId);
        return PIXEL_BYTES;
    }

    @Data
    public static class BulkEmailRequest {
        private List<String> recipients;
        private String subject;
        private String body;
    }
}

