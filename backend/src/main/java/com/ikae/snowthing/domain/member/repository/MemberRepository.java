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
import com.ikae.snowthing.domain.member.entity.Role;

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

    /** 관리자 권한 변경 전에 잠글 행을 고른다. 잠그지 않는 읽기라 회원 표 전체를 잠그지 않는다. */
    @Query("SELECT m.id FROM Member m WHERE m.role = :role AND m.status = :status ORDER BY m.id")
    List<Long> findIdsByRoleAndStatus(
            @Param("role") Role role, @Param("status") MemberStatus status);

    /** ID 순서로 잠가 두 트랜잭션이 같은 행들을 다른 순서로 잡아 교착되지 않게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id IN :ids ORDER BY m.id")
    List<Member> findAllByIdInForUpdate(@Param("ids") List<Long> ids);

    /** 세션과 웹소켓에 든 회원 스냅샷 대신 현재 상태를 읽는다. */
    @Query("SELECT m.status FROM Member m WHERE m.id = :memberId")
    Optional<MemberStatus> findStatusById(@Param("memberId") Long memberId);

    // 관리자 전용 부분 일치 검색. 앞에 %가 붙어 인덱스를 못 타지만 호출이 드물고 페이지 크기를 제한한다.
    @Query(
            "SELECT m FROM Member m WHERE m.nickname LIKE :pattern ESCAPE '!'"
                    + " OR m.email LIKE :pattern ESCAPE '!'"
                    + " ORDER BY m.createdAt DESC, m.id DESC")
    Slice<Member> searchForAdmin(@Param("pattern") String pattern, Pageable pageable);

    long countByCreatedAtAfter(java.time.LocalDateTime createdAt);
}
