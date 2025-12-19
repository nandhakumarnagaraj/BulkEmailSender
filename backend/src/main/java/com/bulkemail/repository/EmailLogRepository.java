package com.bulkemail.repository;

import com.bulkemail.entity.EmailCampaign;
import com.bulkemail.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
	
  Optional<EmailLog> findByTrackingId(String trackingId);

  long countByCampaignAndOpened(EmailCampaign campaign, boolean opened);
}
