package com.ikae.snowthing.domain.contentfilter.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.contentfilter.dto.BannedWordResponse;
import com.ikae.snowthing.domain.contentfilter.entity.BannedWord;
import com.ikae.snowthing.domain.contentfilter.repository.BannedWordRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 금칙어 목록과 메모리 캐시 (IKA-96). 모든 글, 댓글 저장이 비교하므로 DB를 매번 읽지 않는다. 관리자가 추가, 삭제하면 커밋 뒤 다시 읽어 배포나 재시작 없이 바로
 * 반영한다. 서버가 여러 대가 되면 다른 서버는 재시작 전까지 옛 목록을 쓴다(지금은 한 대).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannedWordService {

    private final BannedWordRepository repository;
    private final AdminActionLogger actionLogger;

    private volatile BannedWordMatcher matcher = BannedWordMatcher.empty();

    @PostConstruct
    public void init() {
        reload();
    }

    public void reload() {
        List<BannedWordMatcher.Entry> entries =
                repository.findAll().stream()
                        .map(
                                word ->
                                        new BannedWordMatcher.Entry(
                                                word.getWord(), word.getNormalizedWord()))
                        .toList();
        matcher = new BannedWordMatcher(entries);
        log.info("Loaded {} banned words into memory", entries.size());
    }

    public Optional<String> findMatch(String... texts) {
        return matcher.findMatch(texts);
    }

    @Transactional(readOnly = true)
    public List<BannedWordResponse> getWords() {
        return repository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(BannedWordResponse::from)
                .toList();
    }

    @Transactional
    public BannedWordResponse create(String word) {
        String display = word == null ? "" : word.strip();
        String normalized = BannedWordMatcher.normalize(display);
        if (display.length() > BannedWord.MAX_LENGTH
                || normalized.length() < BannedWordMatcher.MIN_NORMALIZED_LENGTH
                || normalized.length() > BannedWord.MAX_LENGTH) {
            throw new CustomException(ErrorCode.BANNED_WORD_INVALID);
        }
        if (repository.existsByNormalizedWord(normalized)) {
            throw new CustomException(ErrorCode.BANNED_WORD_DUPLICATED);
        }
        BannedWord saved =
                repository.saveAndFlush(new BannedWord(display, normalized, LocalDateTime.now()));
        actionLogger.record(
                AdminActionType.BANNED_WORD_CREATE,
                AdminTargetType.BANNED_WORD,
                saved.getId(),
                display);
        reloadAfterCommit();
        return BannedWordResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        BannedWord word =
                repository
                        .findById(id)
                        .orElseThrow(() -> new CustomException(ErrorCode.BANNED_WORD_NOT_FOUND));
        repository.delete(word);
        actionLogger.record(
                AdminActionType.BANNED_WORD_DELETE,
                AdminTargetType.BANNED_WORD,
                id,
                word.getWord());
        reloadAfterCommit();
    }

    private void reloadAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            reload();
                        }
                    });
            return;
        }
        reload();
    }
}
