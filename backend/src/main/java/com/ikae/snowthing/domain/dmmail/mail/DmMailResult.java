package com.ikae.snowthing.domain.dmmail.mail;

/**
 * SES 호출 한 번의 결과. 재시도해도 되는지를 기준으로 나눈다.
 *
 * <ul>
 *   <li>{@link Accepted}: SES가 수락함
 *   <li>{@link NotSent}: 요청이 나가지 않았음이 확실하거나 SES가 명시적으로 나중에 하라고 거절함. 다시 보내도 중복이 생기지 않는다
 *   <li>{@link Rejected}: 영구 거절. 다시 보내도 같은 결과다
 *   <li>{@link Unknown}: 수락했는지 알 수 없음. 다시 보내면 중복될 수 있다
 * </ul>
 */
public sealed interface DmMailResult {

    String code();

    record Accepted(String messageId) implements DmMailResult {
        @Override
        public String code() {
            return "ACCEPTED";
        }
    }

    record NotSent(String code) implements DmMailResult {}

    record Rejected(String code) implements DmMailResult {}

    record Unknown(String code) implements DmMailResult {}
}
