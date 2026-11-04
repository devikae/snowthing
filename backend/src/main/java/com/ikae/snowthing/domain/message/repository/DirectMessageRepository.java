package com.ikae.snowthing.domain.message.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.message.entity.DirectMessage;

/**
 * 받은함과 보낸함은 첫 페이지와 다음 페이지를 나눠 둔다. {@code (:cursor IS NULL OR id < :cursor)} 한 쿼리로 합치면 조건이 실행 계획에 따라
 * 범위 조건으로 쓰이지 않을 수 있어, 인덱스 (receiver_id, receiver_deleted_at, direct_message_id)의 마지막 열을 범위로 쓰는 모양을
 * 분명히 한다.
 */
public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    @Query(
            "SELECT dm FROM DirectMessage dm JOIN FETCH dm.sender"
                    + " WHERE dm.receiver.id = :memberId AND dm.receiverDeletedAt IS NULL"
                    + " ORDER BY dm.id DESC")
    List<DirectMessage> findReceivedFirstPage(@Param("memberId") Long memberId, Pageable pageable);

    @Query(
            "SELECT dm FROM DirectMessage dm JOIN FETCH dm.sender"
                    + " WHERE dm.receiver.id = :memberId AND dm.receiverDeletedAt IS NULL"
                    + " AND dm.id < :cursor ORDER BY dm.id DESC")
    List<DirectMessage> findReceivedAfter(
            @Param("memberId") Long memberId, @Param("cursor") Long cursor, Pageable pageable);

    @Query(
            "SELECT dm FROM DirectMessage dm JOIN FETCH dm.receiver"
                    + " WHERE dm.sender.id = :memberId AND dm.senderDeletedAt IS NULL"
                    + " ORDER BY dm.id DESC")
    List<DirectMessage> findSentFirstPage(@Param("memberId") Long memberId, Pageable pageable);

    @Query(
            "SELECT dm FROM DirectMessage dm JOIN FETCH dm.receiver"
                    + " WHERE dm.sender.id = :memberId AND dm.senderDeletedAt IS NULL"
                    + " AND dm.id < :cursor ORDER BY dm.id DESC")
    List<DirectMessage> findSentAfter(
            @Param("memberId") Long memberId, @Param("cursor") Long cursor, Pageable pageable);

    @Query(
            "SELECT COUNT(dm) FROM DirectMessage dm WHERE dm.receiver.id = :memberId"
                    + " AND dm.receiverDeletedAt IS NULL AND dm.readAt IS NULL")
    long countUnread(@Param("memberId") Long memberId);

    @Query(
            "SELECT dm FROM DirectMessage dm JOIN FETCH dm.sender JOIN FETCH dm.receiver"
                    + " WHERE dm.id = :id")
    Optional<DirectMessage> findWithMembersById(@Param("id") Long id);

    /** 화면에 보여 줄 판매글. 게시글이 삭제됐거나 정상 상태가 아니면 결과에서 빠지고 화면은 "삭제된 판매글"로 표시한다. */
    @Query(
            "SELECT new com.ikae.snowthing.domain.message.repository.MessageListingRef("
                    + "ml.id, p.publicId, p.title) FROM MarketListing ml JOIN ml.post p"
                    + " WHERE ml.id IN :ids AND p.status = com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL")
    List<MessageListingRef> findVisibleListings(@Param("ids") Collection<Long> ids);
}
