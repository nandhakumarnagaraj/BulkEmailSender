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
import java.util.ArrayList;
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
    private final SpamPreventionService spamPreventionService;
    private final UserRepository userRepository;
    private final EmailCampaignRepository emailCampaignRepository;


    public CampaignStatusResponse createAndSendCampaign(SendCampaignRequest request, String username) {
        User user = userRepository.findByEmail(username).orElseThrow(() -> new IllegalArgumentException("User not found"));

        EmailCampaign campaign = EmailCampaign.builder()
                .campaignName(request.getName())
                .subject(request.getSubject())
                .bodyHtml(request.getBody())
                .user(user)
                .status(EmailCampaign.CampaignStatus.SCHEDULED)
                .totalRecipients(request.getRecipients().size())
                .build();

        if (request.getAttachments() != null) {
            for (var attachmentFile : request.getAttachments()) {
                try {
                    EmailAttachment attachment = EmailAttachment.builder()
                            .campaign(campaign)
                            .fileName(attachmentFile.getOriginalFilename())
                            .fileType(attachmentFile.getContentType())
                            .fileData(attachmentFile.getBytes())
                            .build();
                    campaign.getAttachments().add(attachment);
                } catch (IOException e) {
                    log.error("Failed to read attachment", e);
                }
            }
        }

        List<EmailLog> emailLogs = request.getRecipients().stream().map(recipientEmail ->
                EmailLog.builder()
                        .campaign(campaign)
                        .recipientEmail(recipientEmail)
                        .status(EmailLog.EmailStatus.QUEUED)
                        .build()
        ).collect(Collectors.toList());

        campaign.setEmailLogs(emailLogs);

        EmailCampaign savedCampaign = emailCampaignRepository.save(campaign);

        sendCampaignEmails(savedCampaign);

        return new CampaignStatusResponse(savedCampaign.getId(), savedCampaign.getStatus().name(), savedCampaign.getTotalRecipients());
    }


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
     * Send individual emails to make each recipient feel special
     * Key: Each email is SEPARATE - no BCC, no bulk indicators
     */
    @Async("emailTaskExecutor")
    @Transactional
    public void sendCampaignEmails(EmailCampaign campaign) {
        log.info("Starting campaign: {} with {} recipients", 
            campaign.getCampaignName(), campaign.getTotalRecipients());

        campaign.setStatus(EmailCampaign.CampaignStatus.SENDING);
        campaign.setStartedAt(LocalDateTime.now());

        int emailsSent = 0;
        int batchCounter = 0;

                for (EmailLog emailLog : campaign.getEmailLogs()) {
                    try {
                        // Random delay between emails to appear natural
                        randomDelay();
        
                        sendIndividualEmail(campaign, emailLog);
                        emailsSent++;
                        batchCounter++;
        
                        // Take a longer break after each batch
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
        
                campaign.setSentCount(emailsSent);
                campaign.setCompletedAt(LocalDateTime.now());
                campaign.setStatus(EmailCampaign.CampaignStatus.COMPLETED);
        
                log.info("Campaign completed: {} - Sent: {}/{}",
                    campaign.getCampaignName(), emailsSent, campaign.getTotalRecipients());
            }
        
            /**
             * Send ONE email to ONE recipient
             * This makes it appear as a personal email, not bulk
             */
            private void sendIndividualEmail(EmailCampaign campaign, EmailLog emailLog)
                    throws MessagingException {
        
                String trackingId = UUID.randomUUID().toString();
                emailLog.setTrackingId(trackingId);
                emailLog.setStatus(EmailLog.EmailStatus.SENDING);
                emailLog.setSentAt(LocalDateTime.now());
        
                try {
                    // Build personalized email content
                    String personalizedContent = personalizeContent(
                        campaign.getBodyHtml(),
                        emailLog.getRecipientName()
                    );
        
                    // Add invisible tracking pixel
                    String contentWithTracking = addTrackingPixel(personalizedContent, trackingId);
        
                    // Create professional email message
                    MimeMessage message = createProfessionalEmail(
                        campaign,
                        emailLog,
                        contentWithTracking
                    );
        
                    // Send the email
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
             * Create a professional, non-bulk-looking email
             */
            private MimeMessage createProfessionalEmail(
                    EmailCampaign campaign,
                    EmailLog emailLog,
                    String htmlContent
            ) throws MessagingException {
        
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
                // Set sender with professional name
                try {
					helper.setFrom(new InternetAddress(senderEmail, senderName));
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (MessagingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        
                // Set ONLY ONE recipient (critical for appearing personal)
                helper.setTo(emailLog.getRecipientEmail());
        
                // Set subject
                helper.setSubject(campaign.getSubject());
        
                // Set both HTML and plain text versions
                String plainText = htmlToPlainText(campaign.getBodyText());
                helper.setText(plainText, htmlContent);
        
                // Add professional headers to avoid spam filters
                message.addHeader("X-Mailer", "Enterprise Mail Client");
                message.addHeader("X-Priority", "3"); // Normal priority
                message.addHeader("Precedence", "bulk"); // Honest bulk indicator for servers
                message.addHeader("List-Unsubscribe",
                    String.format("<%s/unsubscribe/%s>", baseUrl, emailLog.getTrackingId()));
        
                // Add custom message ID for tracking
                message.addHeader("Message-ID",
                    String.format("<%s@%s>", UUID.randomUUID(), extractDomain(senderEmail)));
        
                // Add attachments if any
                if (campaign.getAttachments() != null && !campaign.getAttachments().isEmpty()) {
                    campaign.getAttachments().forEach(attachment -> {
                        try {
                            helper.addAttachment(
                                ((EmailAttachment) attachment).getFileName(),
                                () -> new java.io.ByteArrayInputStream(((EmailAttachment) attachment).getFileData()),
                                ((EmailAttachment) attachment).getFileType()
                            );
                        } catch (MessagingException e) {
                            log.error("Failed to add attachment: {}", ((EmailAttachment) attachment).getFileName(), e);
                        }
                    });
                }
        
                return message;
            }
        
            /**
             * Add invisible 1x1 tracking pixel
             */
            private String addTrackingPixel(String htmlContent, String trackingId) {
                String trackingUrl = String.format(
                    "%s/api/v1/tracking/pixel/%s.png",
                    baseUrl,
                    trackingId
                );
        
                String trackingPixel = String.format(
                    "<img src=\"%s\" width=\"1\" height=\"1\" style=\"display:none;border:0;\" alt=\"\" />",
                    trackingUrl
                );
        
                // Add pixel at the end of body
                if (htmlContent.contains("")) {
                    return htmlContent.replace("", trackingPixel + "");
                } else {
                    return htmlContent + trackingPixel;
                }
            }
        
            /**
             * Personalize content with recipient name
             */
            private String personalizeContent(String content, String recipientName) {
                if (recipientName != null && !recipientName.isEmpty()) {
                    content = content.replace("{{name}}", recipientName);
                    content = content.replace("{{firstName}}", recipientName.split(" ")[0]);
                }
                return content;
            }
        
            /**
             * Random delay between emails (appears natural, avoids spam detection)
             */
            private void randomDelay() throws InterruptedException {
                long delay = ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs);
                Thread.sleep(delay);
            }
        
            /**
             * Handle email sending failure
             */
            private void handleEmailFailure(EmailLog emailLog, Exception e) {
                emailLog.setStatus(EmailLog.EmailStatus.FAILED);
                emailLog.setErrorMessage(e.getMessage());
                emailLog.setRetryCount(emailLog.getRetryCount() + 1);
                emailLogRepository.save(emailLog);
            }
        
            private String htmlToPlainText(String html) {
                if (html == null) return "";
                return html.replaceAll("]*>", "").trim();
            }
        
            private String extractDomain(String email) {
                return email.substring(email.indexOf("@") + 1);
            }
        }