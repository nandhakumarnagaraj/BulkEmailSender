package com.bulkemail.repository;

import com.bulkemail.entity.EmailCampaign;
import com.bulkemail.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

	Optional<EmailLog> findByTrackingId(String trackingId);

	long countByCampaignAndOpened(EmailCampaign campaign, boolean opened);

	List<EmailLog> findByCampaignId(Long campaignId);

	List<EmailLog> findByCampaignIdAndStatus(Long campaignId, EmailLog.EmailStatus status);

	long countByCampaignIdAndStatus(Long campaignId, EmailLog.EmailStatus status);

	@Query("SELECT COUNT(l) FROM EmailLog l WHERE l.campaign.id = :campaignId AND l.opened = true")
	long countOpenedEmailsByCampaign(@Param("campaignId") Long campaignId);

	@Query("SELECT COUNT(l) FROM EmailLog l WHERE l.campaign.id = :campaignId AND l.clicked = true")
	long countClickedEmailsByCampaign(@Param("campaignId") Long campaignId);
}