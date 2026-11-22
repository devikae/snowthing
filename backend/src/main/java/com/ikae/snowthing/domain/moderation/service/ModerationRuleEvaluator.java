package com.ikae.snowthing.domain.moderation.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.contentfilter.service.BannedWordService;
import com.ikae.snowthing.domain.moderation.config.ModerationProperties;
import com.ikae.snowthing.domain.moderation.entity.ModerationVerdict;
import com.ikae.snowthing.domain.moderation.repository.ContentModerationRepository;
import com.ikae.snowthing.domain.moderation.repository.ContentModerationRepository.Row;
import com.ikae.snowthing.domain.moderation.repository.ModerationSourceRepository.Source;

import lombok.RequiredArgsConstructor;

/**
 * 1단계 규칙 (IKA-80). 비용이 없다.
 *
 * <p>스팸 신호는 네 묶음이다.
 *
 * <ol>
 *   <li>금칙어: IKA-96 목록을 그대로 쓴다(따로 키워드 목록을 두지 않는다). 글과 댓글은 IKA-96이 저장할 때 이미 보류하므로 여기서는 주로 장터, 카풀, 설질
 *       제보와 관리자가 공개로 돌린 글에서 걸린다.
 *   <li>외부 연락 유도: 텔레그램, 오픈채팅 링크, 카카오톡 ID 표기, 단축 주소 ({@link ModerationTextRules}).
 *   <li>같은 내용 반복: 같은 작성자 또는 같은 IP가 정리한 내용이 같은 글을 짧은 시간에 여러 번(기본 10분 안에 3번째부터).
 *   <li>새 계정 링크: 가입 24시간 안 회원의 글에 눈팅 밖 주소가 3개 이상.
 * </ol>
 *
 * <p>두 묶음 이상이 함께 걸리면 "확실한 스팸"으로 숨긴다(HIDE). 한 묶음만 걸리면 검토 대기(REVIEW)로 두고 2단계가 켜져 있으면 점수로 다시 본다. 정치 글은
 * 정치라는 이유만으로는 숨기지 않고 검토 대기로 보낸다. 같은 글에 스팸 신호 두 묶음이 함께 있거나 2단계에서 숨김 점수가 나오면 다른 글과 똑같이 숨긴다(판정은
 * 규칙과 2단계 중 더 무거운 쪽). 관리자가 문제없음으로 본 내용(같은 해시)은 다시 판정하지 않고 통과시킨다.
 */
@Component
@RequiredArgsConstructor
public class ModerationRuleEvaluator {

    public static final String BANNED_WORD = "BANNED_WORD";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String NEW_ACCOUNT_LINKS = "NEW_ACCOUNT_LINKS";
    public static final String POLITICAL = "POLITICAL";
    public static final String CLEARED_BEFORE = "CLEARED_BEFORE";

    private final ContentModerationRepository repository;
    private final BannedWordService bannedWords;
    private final ModerationProperties properties;

    public RuleResult evaluate(Row row, Source source) {
        if (repository.isClearedByAdmin(row.contentHash())) {
            return new RuleResult(ModerationVerdict.PASS, List.of(CLEARED_BEFORE), true);
        }
        ModerationTextRules.Findings findings =
                ModerationTextRules.inspect(row.targetType(), source.title(), source.body());
        if (!Boolean.TRUE.equals(properties.rules().enabled())) {
            return new RuleResult(
                    ModerationVerdict.PASS,
                    List.of(),
                    findings.normalizedLength() < properties.rules().shortTextLength());
        }
        ModerationProperties.Rules rules = properties.rules();
        List<String> codes = new ArrayList<>();
        int spamGroups = 0;
        if (bannedWords.findMatch(source.title(), source.body()).isPresent()) {
            codes.add(BANNED_WORD);
            spamGroups++;
        }
        if (!findings.contacts().isEmpty()) {
            codes.addAll(findings.contacts());
            spamGroups++;
        }
        if (findings.normalizedLength() >= rules.duplicateMinLength()
                && repository.countDuplicateTargets(
                                        row, row.createdAt().minus(rules.duplicateWindow()))
                                + 1
                        >= rules.duplicateMinCount()) {
            codes.add(DUPLICATE);
            spamGroups++;
        }
        if (source.memberCreatedAt() != null
                && source.memberCreatedAt().isAfter(row.createdAt().minus(rules.newAccountAge()))
                && findings.externalLinkCount() >= rules.newAccountMinLinks()) {
            codes.add(NEW_ACCOUNT_LINKS);
            spamGroups++;
        }
        if (findings.political()) {
            codes.add(POLITICAL);
        }
        ModerationVerdict verdict;
        if (spamGroups >= 2) {
            verdict = ModerationVerdict.HIDE;
        } else if (spamGroups == 1 || findings.political()) {
            verdict = ModerationVerdict.REVIEW;
        } else {
            verdict = ModerationVerdict.PASS;
        }
        boolean skipApi =
                verdict == ModerationVerdict.HIDE
                        || (codes.isEmpty()
                                && findings.normalizedLength() < rules.shortTextLength());
        return new RuleResult(verdict, List.copyOf(codes), skipApi);
    }

    /**
     * @param codes 걸린 규칙
     * @param skipApi 2단계를 부르지 않는다(이미 숨김, 관리자가 문제없음으로 본 내용, 규칙에 하나도 걸리지 않은 짧은 글)
     */
    public record RuleResult(ModerationVerdict verdict, List<String> codes, boolean skipApi) {}
}
