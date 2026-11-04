package com.ikae.snowthing.domain.aimember;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * 하루 활동 시각을 뽑는다. 날짜와 회원으로 시드를 정해서 재시작해도 같은 날에는 같은 계획이 나온다. 그래서 상태를 저장하지 않고 "오늘 이미 쓴 개수"만 세면 남은 몫을
 * 다시 만들 수 있다.
 */
public final class AiActivityPlanner {

    /** 출퇴근·점심·저녁. 끝 시각은 포함하지 않는다. 새벽에는 활동하지 않는다. */
    static final List<int[]> WINDOWS =
            List.of(new int[] {7, 9}, new int[] {12, 13}, new int[] {18, 24});

    static final int MAX_POSTS_PER_MEMBER = 5;
    static final int CHATS_PER_DAY = 5;

    public enum Kind {
        POST,
        CHAT
    }

    /** POST는 memberId가 작성자, CHAT은 null(참여자는 실행 시 고른다). */
    public record Planned(Kind kind, Long memberId, LocalDateTime at) {}

    private AiActivityPlanner() {}

    public static List<Planned> plan(
            LocalDate date,
            List<Long> memberIds,
            Map<Long, Integer> postedToday,
            LocalDateTime now) {
        List<Planned> planned = new ArrayList<>();
        for (Long memberId : memberIds) {
            SplittableRandom random = new SplittableRandom(seed(date, 1, memberId));
            int target = random.nextInt(MAX_POSTS_PER_MEMBER + 1);
            List<LocalDateTime> times = times(date, random, target);
            int remaining = Math.max(0, target - postedToday.getOrDefault(memberId, 0));
            times.stream()
                    .filter(time -> time.isAfter(now))
                    .limit(remaining)
                    .forEach(time -> planned.add(new Planned(Kind.POST, memberId, time)));
        }
        times(date, new SplittableRandom(seed(date, 2, 0)), CHATS_PER_DAY).stream()
                .filter(time -> time.isAfter(now))
                .forEach(time -> planned.add(new Planned(Kind.CHAT, null, time)));
        planned.sort(Comparator.comparing(Planned::at));
        return planned;
    }

    public static boolean isActive(LocalDateTime time) {
        int hour = time.getHour();
        return WINDOWS.stream().anyMatch(window -> hour >= window[0] && hour < window[1]);
    }

    static long seed(LocalDate date, long kind, long id) {
        return (date.toEpochDay() * 1_000_003L + kind) * 1_000_033L + id;
    }

    private static List<LocalDateTime> times(LocalDate date, SplittableRandom random, int count) {
        int total = WINDOWS.stream().mapToInt(window -> (window[1] - window[0]) * 60).sum();
        List<LocalDateTime> times = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int offset = random.nextInt(total);
            for (int[] window : WINDOWS) {
                int length = (window[1] - window[0]) * 60;
                if (offset < length) {
                    times.add(date.atTime(window[0] + offset / 60, offset % 60));
                    break;
                }
                offset -= length;
            }
        }
        times.sort(Comparator.naturalOrder());
        return times;
    }
}
