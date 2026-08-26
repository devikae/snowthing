package com.ikae.snowthing.domain.member.entity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resort")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resort_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "region_name", length = 100)
    private String regionName;

    @Builder
    public Resort(String name, String regionName) {
        this.name = name;
        this.regionName = regionName;
    }
}
