package com.bulkemail.service;

import java.util.List;

public interface IEmailService {
	
	public void sendBulkEmail(List<String> recipients, String subject, String bodyRequest);
	
	public void sendSingleEmail(String recipient, String subject, String bodyContent);
	
	public void trackEmailOpen(String trackingId);
}
