package com.bulkemail.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendCampaignRequest {
    private String name;
    private String subject;
    private String body;
    private List<String> recipients;
    private MultipartFile[] attachments;
}
