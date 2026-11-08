package com.ikae.snowthing.domain.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.market.entity.MarketCategory;
import com.ikae.snowthing.domain.market.entity.MarketListing;
import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TransactionMethod;
import com.ikae.snowthing.domain.market.repository.MarketCategoryRepository;
import com.ikae.snowthing.domain.market.repository.MarketListingRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.service.MemberBlockService;
import com.ikae.snowthing.domain.message.dto.DirectMessageDetailResponse;
import com.ikae.snowthing.domain.message.dto.DirectMessageSendRequest;
import com.ikae.snowthing.domain.message.dto.DirectMessageSummaryResponse;
import com.ikae.snowthing.domain.message.dto.MessageBox;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 쪽지 발송 거부 규칙, 존재 숨김 404, 한쪽 삭제, 읽음 처리, 판매글 연결, 커서 페이징을 실제 MySQL에서 확인한다. */
@SpringBootTest
@Transactional
class DirectMessageServiceTest {

    @Autowired private DirectMessageService messages;
    @Autowired private MemberBlockService blocks;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository postCategories;
    @Autowired private MarketCategoryRepository marketCategories;
    @Autowired private PostRepository posts;
    @Autowired private MarketListingRepository listings;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private CustomUserDetails alice;
    private CustomUserDetails bob;
    private CustomUserDetails carol;

    @BeforeEach
    void setUp() {
        alice = user(saveMember("dm-alice", MemberStatus.ACTIVE, "@example.com"));
        bob = user(saveMember("dm-bob", MemberStatus.ACTIVE, "@example.com"));
        carol = user(saveMember("dm-carol", MemberStatus.ACTIVE, "@example.com"));
    }

    @Nested
    @DisplayName("보내기 거부 규칙")
    class SendRules {

        @Test
        @DisplayName("자기 자신에게는 보낼 수 없다")
        void self() {
            assertError(() -> send(alice, alice, "나에게"), ErrorCode.MESSAGE_SELF_NOT_ALLOWED);
        }

        @Test
        @DisplayName("AI 회원에게 보낸 쪽지는 일반 회원처럼 성공하고 보낸 쪽지함에 남는다")
        void aiMemberReceivesLikeAnyMember() {
            CustomUserDetails ai =
                    user(saveMember("ai-agent99", MemberStatus.ACTIVE, "@snowthing.invalid"));

            String id = send(alice, ai, "안녕하세요");

            assertThat(ids(messages.getSent(alice, null, null))).containsExactly(id);
        }

        @Test
        @DisplayName("정지, 탈퇴, 없는 회원은 같은 문구로 거부한다")
        void unavailableReceivers() {
            Member suspended = saveMember("dm-suspended", MemberStatus.SUSPENDED, "@example.com");
            Member withdrawn = saveMember("dm-withdrawn", MemberStatus.WITHDRAWN, "@example.com");

            for (String publicId :
                    List.of(
                            suspended.getPublicId(),
                            withdrawn.getPublicId(),
                            UUID.randomUUID().toString())) {
                assertThatThrownBy(
                                () ->
                                        messages.send(
                                                alice,
                                                new DirectMessageSendRequest(
                                                        publicId, "안녕하세요", null)))
                        .extracting("errorCode")
                        .isEqualTo(ErrorCode.MESSAGE_RECEIVER_UNAVAILABLE);
            }
            assertThat(ErrorCode.MESSAGE_RECEIVER_UNAVAILABLE.getMessage())
                    .isEqualTo("보낼 수 없는 회원입니다.");
        }

        @Test
        @DisplayName("받는 회원이 나를 차단했으면 차단 사실을 드러내지 않는 같은 문구로 거부한다")
        void receiverBlockedMe() {
            blocks.block(bob, alice.getPublicId());
            assertError(() -> send(alice, bob, "안녕"), ErrorCode.MESSAGE_RECEIVER_UNAVAILABLE);
        }

        @Test
        @DisplayName("내가 차단한 회원에게는 보낼 수 없고, 차단을 풀면 다시 보낼 수 있다")
        void iBlockedReceiver() {
            blocks.block(alice, bob.getPublicId());
            assertError(() -> send(alice, bob, "안녕"), ErrorCode.MESSAGE_RECEIVER_BLOCKED);

            blocks.unblock(alice, bob.getPublicId());
            assertThat(send(alice, bob, "안녕")).isNotBlank();
        }

        @Test
        @DisplayName("차단 전에 받은 쪽지는 받은함에 그대로 남는다")
        void messagesBeforeBlockRemain() {
            send(bob, alice, "차단 전 쪽지");
            blocks.block(alice, bob.getPublicId());
            assertThat(messages.getReceived(alice, null, null).content()).hasSize(1);
        }

        @Test
        @DisplayName("공백만 있는 내용은 거부한다")
        void blankContent() {
            assertError(() -> send(alice, bob, " \n "), ErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    @DisplayName("판매글 연결")
    class MarketListingLink {

        @Test
        @DisplayName("판매글 작성자에게 보내면 양쪽 쪽지에 판매글이 보인다")
        void linkedToSeller() {
            String listing = createListing(bob.getMember(), "보드 팝니다");
            String id = send(alice, bob, "아직 있나요?", listing);

            DirectMessageDetailResponse detail = messages.getDetail(bob, Long.valueOf(id));
            assertThat(detail.marketListing().publicId()).isEqualTo(listing);
            assertThat(detail.marketListing().title()).isEqualTo("보드 팝니다");
            assertThat(messages.getSent(alice, null, null).content().get(0).marketListing())
                    .extracting("publicId", "deleted")
                    .containsExactly(listing, false);
        }

        @Test
        @DisplayName("판매글 작성자가 아닌 회원과 판매글 조합은 거부한다")
        void receiverIsNotSeller() {
            String listing = createListing(bob.getMember(), "보드 팝니다");
            assertError(
                    () -> send(alice, carol, "아직 있나요?", listing),
                    ErrorCode.MESSAGE_MARKET_RECEIVER_MISMATCH);
        }

        @Test
        @DisplayName("없는 판매글은 404다")
        void unknownListing() {
            assertError(
                    () -> send(alice, bob, "아직 있나요?", UUID.randomUUID().toString()),
                    ErrorCode.MARKET_LISTING_NOT_FOUND);
        }

        @Test
        @DisplayName("판매글이 나중에 삭제되면 삭제된 판매글로 표시한다")
        void deletedListingAfterSend() {
            String listing = createListing(bob.getMember(), "보드 팝니다");
            String id = send(alice, bob, "아직 있나요?", listing);
            jdbc.update(
                    "UPDATE post SET is_deleted = true, status = 'DELETED' WHERE public_id = ?",
                    listing);
            entityManager.clear();

            DirectMessageDetailResponse detail = messages.getDetail(bob, Long.valueOf(id));
            assertThat(detail.marketListing().deleted()).isTrue();
            assertThat(detail.marketListing().publicId()).isNull();
        }
    }

    @Nested
    @DisplayName("상세와 읽음")
    class Detail {

        @Test
        @DisplayName("받은 회원이 열 때만 read_at이 기록되고 발신자가 열면 바뀌지 않는다")
        void readOnlyByReceiver() {
            String id = send(alice, bob, "읽어 주세요");

            DirectMessageDetailResponse bySender = messages.getDetail(alice, Long.valueOf(id));
            assertThat(bySender.box()).isEqualTo(MessageBox.SENT);
            assertThat(bySender.read()).isFalse();
            flushAndClear();
            assertThat(readAt(id)).isNull();

            DirectMessageDetailResponse byReceiver = messages.getDetail(bob, Long.valueOf(id));
            assertThat(byReceiver.box()).isEqualTo(MessageBox.RECEIVED);
            assertThat(byReceiver.read()).isTrue();
            assertThat(byReceiver.canReply()).isTrue();
            flushAndClear();
            Object firstReadAt = readAt(id);
            assertThat(firstReadAt).isNotNull();

            messages.getDetail(bob, Long.valueOf(id));
            flushAndClear();
            assertThat(readAt(id)).isEqualTo(firstReadAt);
        }

        @Test
        @DisplayName("남의 쪽지 상세와 삭제는 404다")
        void othersMessage() {
            String id = send(alice, bob, "비밀");
            assertError(
                    () -> messages.getDetail(carol, Long.valueOf(id)), ErrorCode.MESSAGE_NOT_FOUND);
            assertError(
                    () -> messages.delete(carol, Long.valueOf(id)), ErrorCode.MESSAGE_NOT_FOUND);
            assertError(() -> messages.getDetail(carol, -1L), ErrorCode.MESSAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("안 읽은 수는 받은함에서 읽지 않고 지우지 않은 쪽지만 센다")
        void unreadCount() {
            String first = send(alice, bob, "1");
            String second = send(carol, bob, "2");
            send(carol, bob, "3");
            send(bob, alice, "보낸 쪽지는 세지 않는다");
            assertThat(messages.countUnread(bob).count()).isEqualTo(3);

            messages.getDetail(bob, Long.valueOf(first));
            messages.delete(bob, Long.valueOf(second));
            flushAndClear();
            assertThat(messages.countUnread(bob).count()).isEqualTo(1);
        }

        @Test
        @DisplayName("탈퇴한 상대는 탈퇴한 회원으로 표시하고 답장할 수 없다")
        void withdrawnCounterpart() {
            String id = send(alice, bob, "안녕");
            jdbc.update(
                    "UPDATE member SET status = 'WITHDRAWN' WHERE member_id = ?",
                    alice.getMember().getId());
            entityManager.clear();

            DirectMessageDetailResponse detail = messages.getDetail(bob, Long.valueOf(id));
            assertThat(detail.counterpart().nickname()).isEqualTo("탈퇴한 회원");
            assertThat(detail.counterpart().publicId()).isNull();
            assertThat(detail.canReply()).isFalse();
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("한쪽이 지워도 상대 쪽지함에는 남고, 내가 지운 쪽지는 404다")
        void deleteMySideOnly() {
            String id = send(alice, bob, "지워도 남는다");

            messages.delete(alice, Long.valueOf(id));
            flushAndClear();

            assertThat(messages.getSent(alice, null, null).content()).isEmpty();
            assertThat(ids(messages.getReceived(bob, null, null))).containsExactly(id);
            assertThat(messages.getDetail(bob, Long.valueOf(id)).content()).isEqualTo("지워도 남는다");
            assertError(
                    () -> messages.getDetail(alice, Long.valueOf(id)), ErrorCode.MESSAGE_NOT_FOUND);
            assertError(
                    () -> messages.delete(alice, Long.valueOf(id)), ErrorCode.MESSAGE_NOT_FOUND);

            messages.delete(bob, Long.valueOf(id));
            flushAndClear();
            assertThat(messages.getReceived(bob, null, null).content()).isEmpty();
        }
    }

    @Nested
    @DisplayName("목록")
    class Lists {

        @Test
        @DisplayName("받은함은 최신순 커서 페이징이고 다음 커서로 이어서 읽는다")
        void receivedCursor() {
            String first = send(alice, bob, "1");
            String second = send(carol, bob, "2");
            String third = send(alice, bob, "3");
            send(bob, carol, "보낸 쪽지는 받은함에 없다");

            CursorPageResponse<DirectMessageSummaryResponse> page =
                    messages.getReceived(bob, null, 2);
            assertThat(ids(page)).containsExactly(third, second);
            assertThat(page.pageInfo().hasNext()).isTrue();
            assertThat(page.content().get(0).counterpart().nickname())
                    .isEqualTo(alice.getNickname());
            assertThat(page.content().get(0).box()).isEqualTo(MessageBox.RECEIVED);

            CursorPageResponse<DirectMessageSummaryResponse> next =
                    messages.getReceived(bob, Long.valueOf(page.pageInfo().nextCursor()), 2);
            assertThat(ids(next)).containsExactly(first);
            assertThat(next.pageInfo().hasNext()).isFalse();
            assertThat(next.pageInfo().nextCursor()).isNull();
        }

        @Test
        @DisplayName("보낸함은 받은 회원을 상대로 보여 주고 상대가 읽었는지 표시한다")
        void sentList() {
            String id = send(alice, bob, "보낸 쪽지");
            messages.getDetail(bob, Long.valueOf(id));
            flushAndClear();

            DirectMessageSummaryResponse row = messages.getSent(alice, null, null).content().get(0);
            assertThat(row.counterpart().publicId()).isEqualTo(bob.getPublicId());
            assertThat(row.read()).isTrue();
            assertThat(row.box()).isEqualTo(MessageBox.SENT);
        }

        @Test
        @DisplayName("페이지 크기는 1 이상 50 이하만 받는다")
        void pageSize() {
            assertError(
                    () -> messages.getReceived(bob, null, 0), ErrorCode.MESSAGE_INVALID_PAGE_SIZE);
            assertError(() -> messages.getSent(bob, null, 51), ErrorCode.MESSAGE_INVALID_PAGE_SIZE);
        }

        @Test
        @DisplayName("미리보기는 줄바꿈을 한 칸으로 줄이고 80자에서 자른다")
        void preview() {
            send(alice, bob, "첫 줄\n\n둘째 줄 " + "가".repeat(100));
            String preview = messages.getReceived(bob, null, null).content().get(0).preview();
            assertThat(preview).startsWith("첫 줄 둘째 줄 ").endsWith("…").hasSize(81);
        }
    }

    private String send(CustomUserDetails from, CustomUserDetails to, String content) {
        return send(from, to, content, null);
    }

    private String send(
            CustomUserDetails from, CustomUserDetails to, String content, String listing) {
        return messages.send(from, new DirectMessageSendRequest(to.getPublicId(), content, listing))
                .id();
    }

    private static List<String> ids(CursorPageResponse<DirectMessageSummaryResponse> page) {
        return page.content().stream().map(DirectMessageSummaryResponse::id).toList();
    }

    private Object readAt(String id) {
        return jdbc.queryForObject(
                "SELECT read_at FROM direct_message WHERE direct_message_id = ?",
                Object.class,
                Long.valueOf(id));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run).extracting("errorCode").isEqualTo(errorCode);
    }

    private static CustomUserDetails user(Member member) {
        return new CustomUserDetails(member);
    }

    private Member saveMember(String prefix, MemberStatus status, String emailDomain) {
        String unique = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return memberRepository.save(
                Member.builder()
                        .email(unique + emailDomain)
                        .nickname(unique)
                        .role(Role.ROLE_USER)
                        .status(status)
                        .build());
    }

    private String createListing(Member seller, String title) {
        PostCategory postCategory =
                postCategories
                        .findByCode("MARKET")
                        .orElseGet(
                                () ->
                                        postCategories.save(
                                                PostCategory.builder()
                                                        .code("MARKET")
                                                        .name("중고장터")
                                                        .build()));
        MarketCategory category =
                marketCategories
                        .findByCode("SNOWBOARD")
                        .orElseGet(
                                () ->
                                        marketCategories.save(
                                                MarketCategory.builder()
                                                        .code("SNOWBOARD")
                                                        .name("스노보드")
                                                        .active(true)
                                                        .build()));
        Post post =
                posts.save(
                        Post.builder()
                                .member(seller)
                                .category(postCategory)
                                .title(title)
                                .content("판매글 본문")
                                .writerIp("192.0.2.0")
                                .isAnonymous(false)
                                .build());
        listings.save(
                MarketListing.builder()
                        .post(post)
                        .category(category)
                        .productCondition(ProductCondition.GOOD)
                        .transactionMethod(TransactionMethod.DIRECT)
                        .price(10000L)
                        .contact("test-contact")
                        .build());
        return post.getPublicId();
    }
}
