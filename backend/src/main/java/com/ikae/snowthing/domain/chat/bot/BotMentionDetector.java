package com.ikae.snowthing.domain.chat.bot;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class BotMentionDetector {

    private static final Pattern PREFIX =
            Pattern.compile("(?i)^@bot(?:\\s+|$)(.*)$", Pattern.DOTALL);

    public Optional<String> questionFrom(String content) {
        if (content == null) return Optional.empty();
        Matcher matcher = PREFIX.matcher(content.trim());
        if (!matcher.matches()) return Optional.empty();
        String question = matcher.group(1).trim();
        return question.isEmpty() ? Optional.empty() : Optional.of(question);
    }
}
