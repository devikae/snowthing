package com.ikae.snowthing.domain.aimember;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;

import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.Brief;
import com.ikae.snowthing.domain.aimember.persona.Persona;

/**
 * 글마다 소재, 형식, 길이를 뽑아 모델에 준다. 모델에 맡기면 시즌 시점에 끌려 소재와 길이가 하나로 수렴한다.
 *
 * <p>분포는 운영 글의 사람 글(짧은 혼잣말, 한 줄 댓글)을 보고 잡은 값이다. 대량 실측값이 아니다.
 */
final class AiComposeBriefs {

    private record Shape(int weight, String form, String length) {}

    private static final List<Shape> FREE_SHAPES =
            List.of(
                    new Shape(35, "제목이 전부인 한 줄 혼잣말. 본문은 제목을 다듬지 말고 한마디만 덧붙인다", "본문 30자 이내"),
                    new Shape(40, "일상 얘기하듯 툭 던지는 짧은 글. 결론이나 조언으로 맺지 않는다", "본문 30~120자"),
                    new Shape(20, "겪은 일을 두서없이 늘어놓는 글. 끝은 흐지부지해도 된다", "본문 120~400자"),
                    new Shape(5, "한 가지에 꽂혀서 길게 쓰는 글", "본문 400~900자"));
    private static final List<Shape> QNA_SHAPES =
            List.of(
                    new Shape(40, "상황 없이 바로 묻는 짧은 질문", "본문 40자 이내"),
                    new Shape(45, "자기 키, 몸무게, 실력, 가진 장비 중 필요한 것만 대고 묻는 질문", "본문 40~180자"),
                    new Shape(15, "이미 찾아본 내용과 고민을 늘어놓는 긴 질문", "본문 180~500자"));
    private static final List<Shape> COMMENT_SHAPES =
            List.of(
                    new Shape(30, "맞장구나 놀림 한마디. 조언하지 않는다", "15자 이내"),
                    new Shape(40, "짧은 의견 하나. 근거를 붙이지 않는다", "15~40자"),
                    new Shape(25, "자기 경험 한 토막", "40~100자"),
                    new Shape(5, "진지하게 답하는 댓글. 글이 진지한 질문일 때만", "100~200자"));

    private AiComposeBriefs() {}

    static Brief post(Persona persona, String board, SplittableRandom random) {
        Shape shape = pick("QNA".equals(board) ? QNA_SHAPES : FREE_SHAPES, random);
        return new Brief(topic(persona, random), shape.form(), shape.length());
    }

    static Brief comment(SplittableRandom random) {
        Shape shape = pick(COMMENT_SHAPES, random);
        return new Brief(null, shape.form(), shape.length());
    }

    /** 비중대로 게시판을 고른다. 시드가 같으면 결과도 같도록 키 순서를 고정한다. */
    static String board(Persona persona, SplittableRandom random) {
        Map<String, Integer> boards = new TreeMap<>(persona.activity().boards());
        int roll = random.nextInt(boards.values().stream().mapToInt(Integer::intValue).sum());
        for (Map.Entry<String, Integer> entry : boards.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("Board weights are empty");
    }

    private static String topic(Persona persona, SplittableRandom random) {
        List<String> topics = persona.activity().topics();
        return topics.isEmpty() ? null : topics.get(random.nextInt(topics.size()));
    }

    private static Shape pick(List<Shape> shapes, SplittableRandom random) {
        int roll = random.nextInt(shapes.stream().mapToInt(Shape::weight).sum());
        for (Shape shape : shapes) {
            roll -= shape.weight();
            if (roll < 0) {
                return shape;
            }
        }
        return shapes.get(shapes.size() - 1);
    }
}
