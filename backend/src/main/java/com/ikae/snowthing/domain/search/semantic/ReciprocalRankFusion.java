package com.ikae.snowthing.domain.search.semantic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reciprocal Rank Fusion. 서로 다른 검색기의 점수는 단위가 달라 더하지 않고 순위만 쓴다.
 *
 * <p>score(d) = Σ 1 / (k + rank_i(d)), rank는 1부터. 한 목록 안에서 같은 ID가 두 번 나오면 처음 순위만 센다. 점수가 같으면 가장 좋은
 * 순위가 작은 쪽, 그다음 그 순위를 낸 목록의 순서(앞 목록 우선), 그다음 근거 ID 사전순으로 고정한다.
 */
public final class ReciprocalRankFusion {

    public static final int DEFAULT_K = 60;

    private ReciprocalRankFusion() {}

    public static List<String> fuse(List<List<String>> rankings, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }
        Map<String, List<Integer>> ranks = new LinkedHashMap<>();
        Map<String, int[]> best = new HashMap<>();
        for (int listIndex = 0; listIndex < rankings.size(); listIndex++) {
            Set<String> seen = new HashSet<>();
            int rank = 0;
            for (String id : rankings.get(listIndex)) {
                if (id == null || !seen.add(id)) {
                    continue;
                }
                rank++;
                ranks.computeIfAbsent(id, ignored -> new ArrayList<>()).add(rank);
                int[] current = best.get(id);
                if (current == null || rank < current[0]) {
                    best.put(id, new int[] {rank, listIndex});
                }
            }
        }
        Map<String, Double> scores = new HashMap<>();
        ranks.forEach((id, idRanks) -> scores.put(id, score(idRanks, k)));
        List<String> fused = new ArrayList<>(ranks.keySet());
        fused.sort(
                Comparator.comparingDouble((String id) -> scores.get(id))
                        .reversed()
                        .thenComparingInt(id -> best.get(id)[0])
                        .thenComparingInt(id -> best.get(id)[1])
                        .thenComparing(Comparator.naturalOrder()));
        return fused;
    }

    /** 같은 순위 조합이면 목록 순서와 관계없이 같은 값이 나오도록 순위를 정렬한 뒤 더한다. */
    static double score(List<Integer> ranks, int k) {
        double sum = 0;
        for (int rank : ranks.stream().sorted().toList()) {
            sum += 1.0 / (k + rank);
        }
        return sum;
    }
}
