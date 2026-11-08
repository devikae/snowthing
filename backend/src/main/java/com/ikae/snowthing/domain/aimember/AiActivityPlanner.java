package com.ikae.snowthing.domain.aimember;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import com.ikae.snowthing.domain.aimember.persona.Persona.Activity;
import com.ikae.snowthing.domain.aimember.persona.Persona.Window;

/**
 * 하루 활동 시각을 뽑는다. 날짜와 회원으로 시드를 정해서 재시작해도 같은 날에는 같은 계획이 나온다. 그래서 상태를 저장하지 않고 "오늘 이미 쓴 개수"만 세면 남은 몫을
 * 다시 만들 수 있다.
 */
public final class AiActivityPlanner {

    /** 출퇴근·점심·저녁. 끝 시각은 포함하지 않는다. 새벽에는 활동하지 않는다. */
    static final List<Window> DEFAULT_WINDOWS = Activity.DEFAULT.windows();

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
        Map<Long, Activity> activities = new LinkedHashMap<>();
        memberIds.forEach(memberId -> activities.put(memberId, Activity.DEFAULT));
        return plan(date, activities, postedToday, now);
    }

    /** 회원마다 자기 활동 시간대와 빈도로 글 시각을 뽑는다. 채팅은 공통 시간대를 쓴다. */
    public static List<Planned> plan(
            LocalDate date,
            Map<Long, Activity> activities,
            Map<Long, Integer> postedToday,
            LocalDateTime now) {
        List<Planned> planned = new ArrayList<>();
        for (Map.Entry<Long, Activity> entry : activities.entrySet()) {
            Long memberId = entry.getKey();
            SplittableRandom random = new SplittableRandom(seed(date, 1, memberId));
            int target = target(entry.getValue().postsPerDay(), random);
            List<LocalDateTime> times = times(date, random, target, entry.getValue().windows());
            int remaining = Math.max(0, target - postedToday.getOrDefault(memberId, 0));
            times.stream()
                    .filter(time -> time.isAfter(now))
                    .limit(remaining)
                    .forEach(time -> planned.add(new Planned(Kind.POST, memberId, time)));
        }
        times(date, new SplittableRandom(seed(date, 2, 0)), CHATS_PER_DAY, DEFAULT_WINDOWS).stream()
                .filter(time -> time.isAfter(now))
                .forEach(time -> planned.add(new Planned(Kind.CHAT, null, time)));
        planned.sort(Comparator.comparing(Planned::at));
        return planned;
    }

    public static boolean isActive(LocalDateTime time) {
        return Activity.DEFAULT.activeAt(time.getHour());
    }

    static long seed(LocalDate date, long kind, long id) {
        return (date.toEpochDay() * 1_000_003L + kind) * 1_000_033L + id;
    }

    /** 0부터 평균의 두 배까지 고르게 뽑아 평균이 postsPerDay가 되게 한다. 날마다 글 수가 달라진다. */
    static int target(double postsPerDay, SplittableRandom random) {
        double upper = Math.min(postsPerDay * 2, MAX_POSTS_PER_MEMBER);
        int whole = (int) upper;
        int bound = whole + (random.nextDouble() < upper - whole ? 1 : 0);
        return random.nextInt(bound + 1);
    }

    private static List<LocalDateTime> times(
            LocalDate date, SplittableRandom random, int count, List<Window> windows) {
        int total = windows.stream().mapToInt(window -> minutes(window)).sum();
        List<LocalDateTime> times = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int offset = random.nextInt(total);
            for (Window window : windows) {
                int length = minutes(window);
                if (offset < length) {
                    times.add(date.atTime(window.startHour() + offset / 60, offset % 60));
                    break;
                }
                offset -= length;
            }
        }
        times.sort(Comparator.naturalOrder());
        return times;
    }

    private static int minutes(Window window) {
        return (window.endHour() - window.startHour()) * 60;
    }
}
