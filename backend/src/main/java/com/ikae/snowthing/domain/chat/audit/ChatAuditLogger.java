package com.ikae.snowthing.domain.chat.audit;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ChatAuditLogger {

    private static final String UNKNOWN_MEMBER = "ANONYMOUS";
    private static final String UNKNOWN_CLIENT_IP = "UNKNOWN";
    private static final String DEFAULT_CHANNEL = "MAIN_CHAT";
    private static final String UNKNOWN_CONNECTION = "UNKNOWN";
    private static final String NO_CLOSE_REASON = "NONE";
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("chat.audit");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public void log(
            ChatAuditEvent event,
            Long memberId,
            String clientIp,
            String channel,
            String connectionId,
            String closeReason) {
        String timestamp = OffsetDateTime.now().format(ISO_FORMATTER);
        String resolvedMember = memberId != null ? memberId.toString() : UNKNOWN_MEMBER;
        String resolvedIp = sanitize(clientIp, UNKNOWN_CLIENT_IP);
        String resolvedChannel = sanitize(channel, DEFAULT_CHANNEL);
        String resolvedConnectionId = sanitize(connectionId, UNKNOWN_CONNECTION);
        String resolvedCloseReason = sanitize(closeReason, NO_CLOSE_REASON);

        AUDIT_LOG.info(
                "{}\t{}\t{}\t{}\t{}\t{}\t{}",
                timestamp,
                event,
                resolvedMember,
                resolvedIp,
                resolvedChannel,
                resolvedConnectionId,
                resolvedCloseReason);
    }

    private String sanitize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }
}
