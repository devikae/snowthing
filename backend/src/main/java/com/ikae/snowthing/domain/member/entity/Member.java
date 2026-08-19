package com.ikae.snowthing.domain.member.entity;

import com.ikae.snowthing.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

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
    public Member(String publicId, String email, String password, String nickname,
                  String profileImageUrl, String bio, String departureRegion,
                  Long crewId, String crewRole, Role role, MemberStatus status) {
        this.publicId = publicId;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.bio = bio;
        this.departureRegion = departureRegion;
        this.crewId = crewId;
        this.crewRole = crewRole;
        this.role = role != null ? role : Role.ROLE_USER;
        this.status = status != null ? status : MemberStatus.ACTIVE;
    }

    public void updateProfile(String nickname, String bio, String departureRegion, String profileImageUrl) {
        this.nickname = nickname;
        this.bio = bio;
        this.departureRegion = departureRegion;
        this.profileImageUrl = profileImageUrl;
    }
}
