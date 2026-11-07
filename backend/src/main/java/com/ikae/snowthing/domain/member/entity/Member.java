package com.ikae.snowthing.domain.member.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;

import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    public static final String EMAIL_UNIQUE_CONSTRAINT = "email";
    public static final String NICKNAME_UNIQUE_CONSTRAINT = "nickname";
    public static final String WITHDRAWN_NICKNAME = "탈퇴한 회원";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "nickname", nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "bio", length = 255)
    private String bio;

    @Column(name = "departure_region", length = 100)
    private String departureRegion;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "crew_id")
    private Long crewId;

    @Column(name = "crew_role", length = 20)
    private String crewRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.role == null) {
            this.role = Role.ROLE_USER;
        }
        if (this.status == null) {
            this.status = MemberStatus.ACTIVE;
        }
    }

    @Builder
    public Member(
            String publicId,
            String email,
            String password,
            String nickname,
            String profileImageUrl,
            String bio,
            String departureRegion,
            LocalDateTime emailVerifiedAt,
            Long crewId,
            String crewRole,
            Role role,
            MemberStatus status) {
        this.publicId = publicId;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.bio = bio;
        this.departureRegion = departureRegion;
        this.emailVerifiedAt = emailVerifiedAt;
        this.crewId = crewId;
        this.crewRole = crewRole;
        this.role = role != null ? role : Role.ROLE_USER;
        this.status = status != null ? status : MemberStatus.ACTIVE;
    }

    public Member(
            String publicId,
            String email,
            String password,
            String nickname,
            String profileImageUrl,
            String bio,
            String departureRegion,
            Long crewId,
            String crewRole,
            Role role,
            MemberStatus status) {
        this(
                publicId,
                email,
                password,
                nickname,
                profileImageUrl,
                bio,
                departureRegion,
                null,
                crewId,
                crewRole,
                role,
                status);
    }

    public void updateProfile(
            String nickname, String bio, String departureRegion, String profileImageUrl) {
        this.nickname = nickname;
        this.bio = bio;
        this.departureRegion = departureRegion;
        this.profileImageUrl = profileImageUrl;
    }

    public boolean isWithdrawn() {
        return this.status == MemberStatus.WITHDRAWN;
    }

    /** 개인정보를 지우고 탈퇴 상태로 바꾼다. 글과 댓글의 FK가 남도록 행은 지우지 않는다. */
    public void withdraw(String anonymizedEmail, String anonymizedNickname) {
        this.email = anonymizedEmail;
        this.nickname = anonymizedNickname;
        this.password = null;
        this.profileImageUrl = null;
        this.bio = null;
        this.departureRegion = null;
        this.emailVerifiedAt = null;
        this.crewId = null;
        this.crewRole = null;
        this.status = MemberStatus.WITHDRAWN;
    }

    public void verifyEmail(LocalDateTime verifiedAt) {
        this.emailVerifiedAt = verifiedAt;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void suspend() {
        this.status = MemberStatus.SUSPENDED;
    }

    public void reinstate() {
        this.status = MemberStatus.ACTIVE;
    }
}
