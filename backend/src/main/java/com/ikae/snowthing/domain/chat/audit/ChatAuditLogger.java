package com.ikae.snowthing.domain.chat.audit;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ChatAuditLogger {

    private static final String UNKNOWN_CLIENT_IP = "UNKNOWN";
    private static final String DEFAULT_CHANNEL = "MAIN_CHAT";
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("chat.audit");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public void log(Long memberId, String clientIp, String channel) {
        String timestamp = OffsetDateTime.now().format(ISO_FORMATTER);
        String resolvedIp =
                (clientIp != null && !clientIp.isBlank()) ? clientIp : UNKNOWN_CLIENT_IP;
        String resolvedChannel =
                (channel != null && !channel.isBlank()) ? channel : DEFAULT_CHANNEL;

        // Legal compliance format per Communications Secrets Act:
        // [timestamp]\t[member_id]\t[client_ip]\t[channel]
        // Message body is strictly omitted for privacy protection.
        AUDIT_LOG.info("{}\t{}\t{}\t{}", timestamp, memberId, resolvedIp, resolvedChannel);
    }
}
