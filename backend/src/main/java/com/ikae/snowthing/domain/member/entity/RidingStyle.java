package com.ikae.snowthing.domain.member.entity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "riding_style")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RidingStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "style_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50, unique = true)
    private String styleName;

    @Column(name = "description", length = 255)
    private String description;

    @Builder
    public RidingStyle(String styleName, String description) {
        this.styleName = styleName;
        this.description = description;
    }
}
