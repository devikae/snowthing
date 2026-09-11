package com.ikae.snowthing.domain.comment.spike;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class CommentBenchmarkSeedHarnessTest {

    @Test
    void excludesHotspotRootFromRoundRobinForNonStandardScale() {
        int totalComments = 1_234;
        int rootCount = totalComments / 5;
        int replyCount = totalComments - rootCount;
        int hotspotReplyCount = 100;

        long assignedToHotspot =
                IntStream.range(0, replyCount)
                        .map(
                                index ->
                                        CommentBenchmarkSeedHarness.replyRootIndex(
                                                index, hotspotReplyCount, rootCount))
                        .filter(rootIndex -> rootIndex == 0)
                        .count();

        assertThat(assignedToHotspot).isEqualTo(hotspotReplyCount);
        assertThat(
                        IntStream.range(hotspotReplyCount, replyCount)
                                .map(
                                        index ->
                                                CommentBenchmarkSeedHarness.replyRootIndex(
                                                        index, hotspotReplyCount, rootCount)))
                .allMatch(rootIndex -> rootIndex >= 1 && rootIndex < rootCount);
    }
}
