package com.ikae.snowthing.domain.admin.log;

import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 조치를 admin_action_log에 쓴다 (IKA-78).
 *
 * <ul>
 *   <li>조치와 같은 트랜잭션에서만 쓴다(MANDATORY). 조치가 롤백되면 기록도 남지 않고, 기록이 실패하면 조치도 롤백된다.
 *   <li>조치한 관리자는 현재 요청의 인증 정보에서 읽는다. 서비스마다 관리자 인자를 늘리지 않으려는 선택이다. 인증 정보가 없는 호출(스케줄러, 테스트에서 서비스 직접
 *       호출)은 actor가 비어 있다.
 *   <li>사유는 255자로 자른다. 글 본문을 넘기지 않는다.
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AdminActionLogger {

    private final AdminActionLogRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            AdminActionType action, AdminTargetType targetType, Object targetId, String reason) {
        repository.save(
                new AdminActionLog(
                        currentActorId(),
                        action,
                        targetType,
                        String.valueOf(targetId),
                        truncate(reason),
                        LocalDateTime.now()));
    }

    private static Long currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof CustomUserDetails user) {
            return user.getMemberId();
        }
        return null;
    }

    private static String truncate(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.strip();
        return trimmed.length() > AdminActionLog.MAX_REASON_LENGTH
                ? trimmed.substring(0, AdminActionLog.MAX_REASON_LENGTH)
                : trimmed;
    }
}
