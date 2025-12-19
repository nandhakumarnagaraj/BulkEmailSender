package com.bulkemail.service;

import com.bulkemail.dto.CampaignStatusResponse;
import com.bulkemail.dto.SendCampaignRequest;
import com.bulkemail.entity.EmailAttachment;
import com.bulkemail.entity.EmailCampaign;
import com.bulkemail.entity.EmailLog;
import com.bulkemail.entity.User;
import com.bulkemail.repository.EmailCampaignRepository;
import com.bulkemail.repository.EmailLogRepository;
import com.bulkemail.repository.UserRepository;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSenderService {

    private final JavaMailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final EmailCampaignRepository emailCampaignRepository;
    private final UserRepository userRepository;

    @Value("${app.email.sender.email}")
    private String senderEmail;

    @Value("${app.email.sender.name}")
    private String senderName;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.email.delay.min-ms}")
    private long minDelayMs;

    @Value("${app.email.delay.max-ms}")
    private long maxDelayMs;

    @Value("${app.email.batch-size}")
    private int batchSize;

    @Value("${app.email.batch-delay-ms}")
    private long batchDelayMs;

    /**
     * Create campaign and queue emails
     */
    @Transactional
    public CampaignStatusResponse createAndSendCampaign(SendCampaignRequest request, String username) {
        log.info("Creating campaign: {} from user: {}", request.getName(), username);

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> {
                    log.error("User not found: {}", username);
                    return new IllegalArgumentException("User not found");
                });

        EmailCampaign campaign = EmailCampaign.builder()
                .campaignName(request.getName())
                .subject(request.getSubject())
                .bodyHtml(request.getBody())
                .user(user)
                .status(EmailCampaign.CampaignStatus.SCHEDULED)
                .totalRecipients(request.getRecipients().size())
                .sentCount(0)
                .failedCount(0)
                .deliveredCount(0)
                .openedCount(0)
                .build();

        // Process attachments
        if (request.getAttachments() != null && request.getAttachments().length > 0) {
            for (var attachmentFile : request.getAttachments()) {
                try {
                    EmailAttachment attachment = EmailAttachment.builder()
                            .campaign(campaign)
                            .fileName(attachmentFile.getOriginalFilename())
                            .fileType(attachmentFile.getContentType())
                            .fileSize(attachmentFile.getSize())
                            .fileData(attachmentFile.getBytes())
                            .build();
                    campaign.getAttachments().add(attachment);
                    log.debug("Added attachment: {}", attachmentFile.getOriginalFilename());
                } catch (IOException e) {
                    log.error("Failed to read attachment: {}", attachmentFile.getOriginalFilename(), e);
                }
            }
        }

        // Create email logs for each recipient
        List<EmailLog> emailLogs = request.getRecipients().stream()
                .map(recipientEmail -> EmailLog.builder()
                        .campaign(campaign)
                        .recipientEmail(recipientEmail)
                        .status(EmailLog.EmailStatus.QUEUED)
                        .openCount(0)
                        .clickCount(0)
                        .retryCount(0)
                        .build())
                .collect(Collectors.toList());

        campaign.setEmailLogs(emailLogs);

        // Save campaign and logs
        EmailCampaign savedCampaign = emailCampaignRepository.save(campaign);
        log.info("Campaign saved with ID: {}", savedCampaign.getId());

        // Send emails asynchronously
        sendCampaignEmails(savedCampaign);

        return CampaignStatusResponse.builder()
                .campaignId(savedCampaign.getId())
                .status(savedCampaign.getStatus().name())
                .totalEmails(savedCampaign.getTotalRecipients())
                .build();
    }

    /**
     * Send emails asynchronously
     */
    @Async("taskExecutor")
    @Transactional
    public void sendCampaignEmails(EmailCampaign campaign) {
        log.info("Starting email send for campaign: {} with {} recipients", 
            campaign.getCampaignName(), campaign.getTotalRecipients());

        campaign.setStatus(EmailCampaign.CampaignStatus.SENDING);
        campaign.setStartedAt(LocalDateTime.now());
        emailCampaignRepository.save(campaign);

        int emailsSent = 0;
        int batchCounter = 0;

        for (EmailLog emailLog : campaign.getEmailLogs()) {
            try {
                // Random delay between emails
                randomDelay();

                sendIndividualEmail(campaign, emailLog);
                emailsSent++;
                batchCounter++;

                // Take longer break after batch
                if (batchCounter >= batchSize) {
                    log.info("Batch complete. Taking extended break...");
                    Thread.sleep(batchDelayMs);
                    batchCounter = 0;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Campaign interrupted: {}", campaign.getId());
                campaign.setStatus(EmailCampaign.CampaignStatus.PAUSED);
                break;
            } catch (Exception e) {
                log.error("Failed to send email to: {}", emailLog.getRecipientEmail(), e);
                handleEmailFailure(emailLog, e);
            }
        }

        // Update campaign stats
        campaign.setSentCount(emailsSent);
        campaign.setCompletedAt(LocalDateTime.now());
        campaign.setStatus(EmailCampaign.CampaignStatus.COMPLETED);
        emailCampaignRepository.save(campaign);

        log.info("Campaign completed: {} - Sent: {}/{}", 
            campaign.getCampaignName(), emailsSent, campaign.getTotalRecipients());
    }

    /**
     * Send individual email to one recipient
     */
    private void sendIndividualEmail(EmailCampaign campaign, EmailLog emailLog)
            throws MessagingException, UnsupportedEncodingException {

        String trackingId = UUID.randomUUID().toString();
        emailLog.setTrackingId(trackingId);
        emailLog.setStatus(EmailLog.EmailStatus.SENDING);
        emailLog.setSentAt(LocalDateTime.now());

        try {
            // Personalize content
            String personalizedContent = personalizeContent(
                campaign.getBodyHtml(),
                emailLog.getRecipientName()
            );

            // Add tracking pixel
            String contentWithTracking = addTrackingPixel(personalizedContent, trackingId);

            // Create email message
            MimeMessage message = createProfessionalEmail(
                campaign,
                emailLog,
                contentWithTracking
            );

            // Send email
            mailSender.send(message);

            // Update status
            emailLog.setStatus(EmailLog.EmailStatus.SENT);
            emailLog.setMessageId(message.getMessageID());
            emailLogRepository.save(emailLog);

            log.debug("Email sent successfully to: {}", emailLog.getRecipientEmail());

        } catch (MessagingException e) {
            handleEmailFailure(emailLog, e);
            throw e;
        }
    }

    /**
     * Create professional email message
     */
    private MimeMessage createProfessionalEmail(
            EmailCampaign campaign,
            EmailLog emailLog,
            String htmlContent
    ) throws MessagingException, UnsupportedEncodingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        // FIX: Proper exception handling for setFrom
        helper.setFrom(new InternetAddress(senderEmail, senderName));

        // Set recipient
        helper.setTo(emailLog.getRecipientEmail());

        // Set subject
        helper.setSubject(campaign.getSubject());

        // FIX: Use bodyHtml instead of bodyText
        String plainText = htmlToPlainText(htmlContent);
        helper.setText(plainText, htmlContent);

        // Add professional headers
        message.addHeader("X-Mailer", "Enterprise Mail Client");
        message.addHeader("X-Priority", "3");
        message.addHeader("Precedence", "bulk");
        message.addHeader("List-Unsubscribe",
            String.format("<%s/unsubscribe/%s>", baseUrl, emailLog.getTrackingId()));

        message.addHeader("Message-ID",
            String.format("<%s@%s>", UUID.randomUUID(), extractDomain(senderEmail)));

        // Add attachments
        if (campaign.getAttachments() != null && !campaign.getAttachments().isEmpty()) {
            for (EmailAttachment attachment : campaign.getAttachments()) {
                helper.addAttachment(
                    attachment.getFileName(),
                    () -> new java.io.ByteArrayInputStream(attachment.getFileData()),
                    attachment.getFileType()
                );
            }
        }

        return message;
    }

    /**
     * Add invisible tracking pixel
     */
    private String addTrackingPixel(String htmlContent, String trackingId) {
        String trackingUrl = String.format(
            "%s/api/v1/email/track/%s.png",
            baseUrl,
            trackingId
        );

        String trackingPixel = String.format(
            "<img src=\"%s\" width=\"1\" height=\"1\" style=\"display:none;border:0;\" alt=\"\" />",
            trackingUrl
        );

        // FIX: Proper HTML closing tag check
        if (htmlContent.contains("</body>")) {
            return htmlContent.replace("</body>", trackingPixel + "</body>");
        } else {
            return htmlContent + trackingPixel;
        }
    }

    /**
     * Personalize content with recipient name
     */
    private String personalizeContent(String content, String recipientName) {
        if (recipientName != null && !recipientName.trim().isEmpty()) {
            content = content.replace("{{name}}", recipientName);
            String firstName = recipientName.split(" ")[0];
            content = content.replace("{{firstName}}", firstName);
        }
        return content;
    }

    /**
     * Random delay between emails
     */
    private void randomDelay() throws InterruptedException {
        long delay = ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs);
        Thread.sleep(delay);
    }

    /**
     * Handle email failure
     */
    private void handleEmailFailure(EmailLog emailLog, Exception e) {
        emailLog.setStatus(EmailLog.EmailStatus.FAILED);
        emailLog.setErrorMessage(e.getMessage());
        emailLog.setRetryCount(emailLog.getRetryCount() + 1);
        emailLogRepository.save(emailLog);
        log.error("Email failed for: {} - Error: {}", emailLog.getRecipientEmail(), e.getMessage());
    }

    /**
     * Convert HTML to plain text
     */
    private String htmlToPlainText(String html) {
        if (html == null) return "";
        // FIX: Correct regex pattern
        return html.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * Extract domain from email
     */
    private String extractDomain(String email) {
        return email.substring(email.indexOf("@") + 1);
    }
}
