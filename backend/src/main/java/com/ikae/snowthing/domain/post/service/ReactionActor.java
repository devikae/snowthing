package com.ikae.snowthing.domain.post.service;

public record ReactionActor(
    Long memberId,
    String anonymousVoterId,
    String writerIp
) {

    public static ReactionActor member(Long memberId, String writerIp) {
        return new ReactionActor(memberId, null, writerIp);
    }

    public static ReactionActor anonymous(String anonymousVoterId, String writerIp) {
        return new ReactionActor(null, anonymousVoterId, writerIp);
    }

    public boolean isMember() {
        return memberId != null;
    }
}
