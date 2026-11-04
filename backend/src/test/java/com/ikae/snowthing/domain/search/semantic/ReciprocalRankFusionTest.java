package com.ikae.snowthing.domain.search.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

    @Test
    @DisplayName("순위만 쓴다: 두 목록 모두에 있는 문서가 한 목록 1위보다 앞설 수 있다")
    void fusesByRankOnly() {
        // a: 1위 + 없음 = 1/61, b: 2위 + 2위 = 2/62. b가 앞선다.
        List<String> fused =
                ReciprocalRankFusion.fuse(List.of(List.of("a", "b", "c"), List.of("x", "b")), 60);

        assertThat(fused).containsExactly("b", "a", "x", "c");
        assertThat(ReciprocalRankFusion.score(List.of(2, 2), 60))
                .isGreaterThan(ReciprocalRankFusion.score(List.of(1), 60));
    }

    @Test
    @DisplayName("k가 작으면 상위 순위의 영향이 커진다")
    void smallKFavorsTopRank() {
        List<List<String>> rankings = List.of(List.of("a", "b"), List.of("c", "b"));

        assertThat(ReciprocalRankFusion.fuse(rankings, 60).getFirst()).isEqualTo("b");
        // k=1이면 1위 하나(1/2)가 4위 두 번(1/5 + 1/5)보다 크다.
        assertThat(
                        ReciprocalRankFusion.fuse(
                                List.of(List.of("a", "x", "y", "b"), List.of("c", "z", "w", "b")),
                                1))
                .startsWith("a", "c");
    }

    @Test
    @DisplayName("점수가 같으면 가장 좋은 순위, 그다음 앞 목록, 그다음 근거 ID 순으로 고정한다")
    void breaksTiesDeterministically() {
        // a와 c는 모두 1위 하나씩 (1/61). 가장 좋은 순위가 같고, a가 앞 목록이다.
        assertThat(ReciprocalRankFusion.fuse(List.of(List.of("a"), List.of("c")), 60))
                .containsExactly("a", "c");
        assertThat(ReciprocalRankFusion.fuse(List.of(List.of("c"), List.of("a")), 60))
                .containsExactly("c", "a");
        // (1위, 3위)와 (3위, 1위)는 같은 점수, 같은 가장 좋은 순위(1), 가장 좋은 순위를 낸 목록이 다르다.
        assertThat(
                        ReciprocalRankFusion.fuse(
                                List.of(List.of("p", "m", "q"), List.of("q", "n", "p")), 60))
                .startsWith("p", "q");
        // 순위 조합이 같으면 목록 순서와 관계없이 점수가 정확히 같다.
        assertThat(ReciprocalRankFusion.score(List.of(1, 3), 60))
                .isEqualTo(ReciprocalRankFusion.score(List.of(3, 1), 60));
    }

    @Test
    @DisplayName("한 목록이 비면 다른 목록 순서를 그대로 쓴다")
    void oneListEmpty() {
        assertThat(ReciprocalRankFusion.fuse(List.of(List.of(), List.of("v1", "v2", "v3")), 60))
                .containsExactly("v1", "v2", "v3");
        assertThat(ReciprocalRankFusion.fuse(List.of(List.of("k1", "k2"), List.of()), 60))
                .containsExactly("k1", "k2");
        assertThat(ReciprocalRankFusion.fuse(List.of(List.of(), List.of()), 60)).isEmpty();
    }

    @Test
    @DisplayName("두 목록에 같은 문서가 있으면 한 번만 나오고 점수가 더해진다. 한 목록 안 중복은 처음 순위만 센다")
    void duplicates() {
        List<String> fused =
                ReciprocalRankFusion.fuse(
                        List.of(List.of("a", "b", "a", "c"), List.of("c", "a")), 60);

        assertThat(fused).containsExactly("a", "c", "b");
        assertThat(fused).doesNotHaveDuplicates();
        // 목록 1의 두 번째 a는 무시되므로 c는 3위(2위 아님)다: c = 1/63 + 1/61, b = 1/62.
        assertThat(ReciprocalRankFusion.score(List.of(3, 1), 60))
                .isGreaterThan(ReciprocalRankFusion.score(List.of(2), 60));
    }

    @Test
    @DisplayName("k는 1 이상이어야 한다")
    void rejectsInvalidK() {
        assertThatThrownBy(() -> ReciprocalRankFusion.fuse(List.of(List.of("a")), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
