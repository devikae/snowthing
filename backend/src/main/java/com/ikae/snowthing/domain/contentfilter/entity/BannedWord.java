package com.ikae.snowthing.domain.contentfilter.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자가 등록한 금칙어 (IKA-96). 비교는 normalizedWord로 한다. 공백과 기호를 뺀 소문자라서 "카 지 노"와 "카지노"를 같은 금칙어로 본다. 같은 비교
 * 값은 한 번만 등록된다.
 */
@Entity
@Table(name = "banned_word")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BannedWord {

    public static final int MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "banned_word_id")
    private Long id;

    @Column(name = "word", nullable = false, length = MAX_LENGTH)
    private String word;

    @Column(name = "normalized_word", nullable = false, unique = true, length = MAX_LENGTH)
    private String normalizedWord;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public BannedWord(String word, String normalizedWord, LocalDateTime createdAt) {
        this.word = word;
        this.normalizedWord = normalizedWord;
        this.createdAt = createdAt;
    }
}
