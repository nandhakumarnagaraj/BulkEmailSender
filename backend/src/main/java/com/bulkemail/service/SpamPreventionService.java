package com.bulkemail.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@Slf4j
public class SpamPreventionService {

    private static final Pattern SPAM_WORDS = Pattern.compile(
        "(?i)(free|winner|congratulations|click here|buy now|limited time|act now|" +
        "earn money|weight loss|viagra|casino|lottery|million dollars)"
    );

    /**
     * Check if content might trigger spam filters
     */
    public SpamScore analyzeContent(String subject, String body) {
        int score = 0;
        StringBuilder warnings = new StringBuilder();

        // Check subject
        if (subject.length() > 100) {
            score += 2;
            warnings.append("Subject too long. ");
        }
        if (subject.toUpperCase().equals(subject)) {
            score += 3;
            warnings.append("All caps subject. ");
        }
        if (SPAM_WORDS.matcher(subject).find()) {
            score += 5;
            warnings.append("Spam words in subject. ");
        }

        // Check body
        if (body.contains("!!!!") || body.contains("????")) {
            score += 2;
            warnings.append("Excessive punctuation. ");
        }
        if (SPAM_WORDS.matcher(body).find()) {
            score += 3;
            warnings.append("Spam words in body. ");
        }

        String risk = score < 5 ? "LOW" : score < 10 ? "MEDIUM" : "HIGH";

        return new SpamScore(score, risk, warnings.toString());
    }

    public record SpamScore(int score, String risk, String warnings) {}
}