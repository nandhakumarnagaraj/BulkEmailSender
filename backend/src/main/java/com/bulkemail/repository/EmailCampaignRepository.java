package com.bulkemail.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bulkemail.entity.EmailCampaign;

@Repository
public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, Long> {

	List<EmailCampaign> findByUserId(Long userId);

	List<EmailCampaign> findByUserIdAndStatus(Long userId, EmailCampaign.CampaignStatus status);

	@Query("SELECT c FROM EmailCampaign c WHERE c.user.id = :userId ORDER BY c.createdAt DESC")
	List<EmailCampaign> findUserCampaignsOrderByLatest(@Param("userId") Long userId);

	@Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM EmailCampaign c WHERE c.id = :campaignId AND c.user.id = :userId")
	boolean existsByIdAndUserId(@Param("campaignId") Long campaignId, @Param("userId") Long userId);
}
