package com.bulkemail.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IEmailService {

    void sendBulkEmail(List<String> recipients, String subject, String body, List<MultipartFile> attachments);

    void sendSingleEmail(String recipient, String subject, String bodyContent, List<MultipartFile> attachments);

    void trackEmailOpen(String trackingId);
}

