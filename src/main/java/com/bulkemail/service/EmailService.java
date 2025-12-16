package com.bulkemail.service;

import com.bulkemail.entity.EmailLog;
import com.bulkemail.repository.EmailLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService implements IEmailService {

  private final JavaMailSender mailSender;
  private final EmailLogRepository emailLogRepository;

  @Value("${app.base-url}")
  private String baseUrl;

  @Value("${spring.mail.username}")
  private String senderEmail;

  @Async
  @Override
  public void sendBulkEmail(List<String> recipients, String subject, String bodyRequest) {
    log.info("Starting bulk email send to {} recipients", recipients.size());

    for (String recipient : recipients) {
      try {
        sendSingleEmail(recipient, subject, bodyRequest);
        // Respectful delay to avoid overwhelming SMTP server
        Thread.sleep(500);
      } catch (Exception e) {
        log.error("Failed to send email to {}", recipient, e);
      }
    }

    log.info("Completed bulk email send");
  }
  @Override
  public void sendSingleEmail(String recipient, String subject, String bodyContent) {
    String trackingId = UUID.randomUUID().toString();

    // 1. Create and Save Log (Initial Status)
    EmailLog emailLog = new EmailLog();
    emailLog.setRecipient(recipient);
    emailLog.setSubject(subject);
    emailLog.setTrackingId(trackingId);
    emailLog.setStatus(EmailLog.EmailStatus.SENT); // Optimistic init, update on failure
    emailLog.setSentAt(LocalDateTime.now());
    emailLog.setOpened(false);

    try {
      // 2. Embed Tracking Pixel
      String trackingPixelUrl = baseUrl + "/track/" + trackingId + ".png";
      String trackingHtml = String
          .format("<img src=\"%s\" width=\"1\" height=\"1\" style=\"display:none;\" alt=\"\" />", trackingPixelUrl);
      String finalHtmlContent = bodyContent + "<br/>" + trackingHtml;

      // 3. Prepare Email
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true);

      helper.setFrom(senderEmail);
      helper.setTo(recipient);
      helper.setSubject(subject);
      helper.setText(finalHtmlContent, true); // true = HTML

      // 4. Send
      mailSender.send(message);

      // 5. Save Success
      emailLogRepository.save(emailLog);
      log.info("Sent email to {}", recipient);

    } catch (MessagingException e) {
      emailLog.setStatus(EmailLog.EmailStatus.FAILED);
      emailLog.setErrorMessage(e.getMessage());
      emailLogRepository.save(emailLog);
      log.error("Error sending to {}", recipient, e);
    }
  }
  @Override
  public void trackEmailOpen(String trackingId) {
    emailLogRepository.findByTrackingId(trackingId).ifPresent(log -> {
      if (!log.isOpened()) {
        log.setOpened(true);
        log.setOpenedAt(LocalDateTime.now());
        emailLogRepository.save(log);
      }
    });
  }
}
