package com.ikae.snowthing.domain.post.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "post_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Builder
    public PostCategory(String name, String code) {
        this.name = name;
        this.code = code;
    }
}
