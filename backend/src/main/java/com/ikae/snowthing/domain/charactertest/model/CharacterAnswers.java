package com.ikae.snowthing.domain.charactertest.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 답변 15자리. 앞 12자리는 유형 질문으로 A 또는 B이고, 3개씩 한 축이다. 축별 다수결이라 동점이 없다.
 *
 * <p>A가 앞 글자다: 1~3번 E/I, 4~6번 S/N, 7~9번 T/F, 10~12번 J/P.
 *
 * <p>뒤 3자리는 타는 습관 질문(코스, 쉬는 타이밍, 타는 시간)으로 A, B, C 순서의 척도다. 유형에는 영향이 없고 궁합 점수에만 쓴다.
 */
public record CharacterAnswers(String value) {

    public static final String REGEXP = "^[AB]{12}[ABC]{3}$";
    public static final int TYPE_QUESTION_COUNT = 12;
    public static final int QUESTION_COUNT = 15;
    private static final Pattern PATTERN = Pattern.compile(REGEXP);
    private static final int AXIS_SIZE = 3;
    private static final String[] AXES = {"EI", "SN", "TF", "JP"};

    public CharacterAnswers {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("답변 형식이 올바르지 않습니다.");
        }
    }

    public CharacterType type() {
        StringBuilder code = new StringBuilder(AXES.length);
        for (int axis = 0; axis < AXES.length; axis++) {
            code.append(AXES[axis].charAt(frontCount(axis) >= 2 ? 0 : 1));
        }
        return CharacterType.valueOf(code.toString());
    }

    /** 스탯은 각 축에서 해당 쪽을 고른 답의 수(0~3)다. */
    public CharacterStats stats() {
        return new CharacterStats(
                frontCount(0), AXIS_SIZE - frontCount(1), AXIS_SIZE - frontCount(2), frontCount(3));
    }

    /** 결과의 근거로 보여 줄 질문 번호(1부터). 축마다 다수 쪽을 고른 첫 질문 하나다. */
    public List<Integer> highlightQuestions() {
        List<Integer> questions = new ArrayList<>(AXES.length);
        for (int axis = 0; axis < AXES.length; axis++) {
            char majority = frontCount(axis) >= 2 ? 'A' : 'B';
            int start = axis * AXIS_SIZE;
            for (int index = start; index < start + AXIS_SIZE; index++) {
                if (value.charAt(index) == majority) {
                    questions.add(index + 1);
                    break;
                }
            }
        }
        return List.copyOf(questions);
    }

    /** 두 사람의 유형 답변 12개 중 같은 수. */
    public int matchCount(CharacterAnswers other) {
        int count = 0;
        for (int index = 0; index < TYPE_QUESTION_COUNT; index++) {
            if (value.charAt(index) == other.value.charAt(index)) {
                count++;
            }
        }
        return count;
    }

    /** 습관 질문별 척도 차이(0~2). 다른 사람의 답 자체는 알 수 없고 얼마나 다른지만 남는다. */
    public List<Integer> habitGaps(CharacterAnswers other) {
        List<Integer> gaps = new ArrayList<>(QUESTION_COUNT - TYPE_QUESTION_COUNT);
        for (int index = TYPE_QUESTION_COUNT; index < QUESTION_COUNT; index++) {
            gaps.add(Math.abs(value.charAt(index) - other.value.charAt(index)));
        }
        return List.copyOf(gaps);
    }

    private int frontCount(int axis) {
        int start = axis * AXIS_SIZE;
        int count = 0;
        for (int index = start; index < start + AXIS_SIZE; index++) {
            if (value.charAt(index) == 'A') {
                count++;
            }
        }
        return count;
    }
}
