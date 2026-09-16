package com.ikae.snowthing.domain.comment.spike;

import java.util.List;
import java.util.Random;

/** 고정 seed로 실제 스키 커뮤니티 문장처럼 보이는 테스트 콘텐츠를 생성한다. */
final class RealisticContentGenerator {
    private static final List<String> RESORTS = List.of("용평", "휘닉스", "하이원", "곤지암", "무주");
    private static final List<String> TOPICS =
            List.of("설질", "리프트 대기", "야간 개장", "장비 렌탈", "초보자 슬로프", "카풀");
    private static final List<String> OPINIONS =
            List.of("생각보다 만족스러웠습니다", "주말에는 조금 붐볐습니다", "초보자도 타기 편했습니다", "오전 설질이 특히 좋았습니다");
    private static final List<String> REPLIES =
            List.of(
                    "저도 같은 경험이었어요",
                    "오전 9시 전에는 대기가 짧았습니다",
                    "정상 쪽이 더 좋았다는 후기가 많더라고요",
                    "도움 되는 정보 감사합니다");

    private RealisticContentGenerator() {}

    static String postTitle(Random random, int index) {
        return RESORTS.get(random.nextInt(RESORTS.size()))
                + " "
                + TOPICS.get(random.nextInt(TOPICS.size()))
                + " 후기와 팁 "
                + index;
    }

    static String postBody(Random random, int index) {
        return RESORTS.get(random.nextInt(RESORTS.size()))
                + "에 다녀온 기록입니다. "
                + TOPICS.get(random.nextInt(TOPICS.size()))
                + " 상태를 직접 확인했고, "
                + OPINIONS.get(random.nextInt(OPINIONS.size()))
                + ". 방문 예정인 분들께 참고가 되었으면 합니다. (후기 "
                + index
                + ")";
    }

    static String rootComment(Random random, int index) {
        return List.of(
                                "현장 정보 감사합니다",
                                "이번 주말에 방문하려는데 참고할게요",
                                "사진으로 보니 설질이 좋아 보이네요",
                                "저는 지난주에 비슷하게 느꼈습니다")
                        .get(random.nextInt(4))
                + " (댓글 "
                + index
                + ") [benchmark-sprint04-root-"
                + index
                + "]";
    }

    static String reply(Random random, int index) {
        return REPLIES.get(random.nextInt(REPLIES.size()))
                + " (답글 "
                + index
                + ") [benchmark-sprint04-reply-"
                + index
                + "]";
    }
}
