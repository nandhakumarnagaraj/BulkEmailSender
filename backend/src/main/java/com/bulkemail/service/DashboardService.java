package com.bulkemail.service;

import com.bulkemail.dto.CampaignReportDTO;
import com.bulkemail.repository.EmailCampaignRepository;
import com.bulkemail.repository.EmailLogRepository;
import com.bulkemail.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EmailCampaignRepository campaignRepository;
    private final EmailLogRepository logRepository;
    private final UserRepository userRepository;

    public List getUserCampaigns(String username) {
        return null;
    }

    public CampaignReportDTO getCampaignReport(Long campaignId, String name) {
        return null;
    }

    public List getCampaignRecipients(Long campaignId, String status, String name) {
        return null;
    }
}
