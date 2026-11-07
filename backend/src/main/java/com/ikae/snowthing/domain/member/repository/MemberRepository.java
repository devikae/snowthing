package com.ikae.snowthing.domain.member.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByProfileImageUrl(String profileImageUrl);

    Optional<Member> findByPublicId(String publicId);

    // nickname UNIQUE 인덱스의 앞부분 범위 검색을 쓴다. 닉네임 순이라 정확히 같은 닉네임이 맨 위에 온다.
    @Query(
            "SELECT m FROM Member m WHERE m.nickname LIKE :prefix ESCAPE '!'"
                    + " AND m.status = com.ikae.snowthing.domain.member.entity.MemberStatus.ACTIVE"
                    + " AND m.id <> :viewerId ORDER BY m.nickname ASC, m.id ASC")
    List<Member> searchActiveByNicknamePrefix(
            @Param("prefix") String prefix, @Param("viewerId") Long viewerId, Pageable pageable);

    Optional<Member> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.email = :email")
    Optional<Member> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.publicId = :publicId")
    Optional<Member> findByPublicIdForUpdate(@Param("publicId") String publicId);

    /** 세션과 웹소켓에 든 회원 스냅샷 대신 현재 상태를 읽는다. */
    @Query("SELECT m.status FROM Member m WHERE m.id = :memberId")
    Optional<MemberStatus> findStatusById(@Param("memberId") Long memberId);

    // 관리자 전용 부분 일치 검색. 앞에 %가 붙어 인덱스를 못 타지만 호출이 드물고 페이지 크기를 제한한다.
    @Query(
            "SELECT m FROM Member m WHERE m.nickname LIKE :pattern ESCAPE '!'"
                    + " OR m.email LIKE :pattern ESCAPE '!'"
                    + " ORDER BY m.createdAt DESC, m.id DESC")
    Slice<Member> searchForAdmin(@Param("pattern") String pattern, Pageable pageable);
}
