package com.ikae.snowthing.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.market.dto.MarketListingCreateRequest;
import com.ikae.snowthing.domain.market.dto.MarketListingUpdateRequest;
import com.ikae.snowthing.domain.market.entity.MarketCategory;
import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TransactionMethod;
import com.ikae.snowthing.domain.market.repository.MarketCategoryRepository;
import com.ikae.snowthing.domain.market.repository.MarketListingRepository;
import com.ikae.snowthing.domain.market.service.MarketListingService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostResponse;
import com.ikae.snowthing.domain.post.dto.PostUpdateRequest;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 글과 판매글 본문은 저장 전에 정화하고, 글자 그대로 보이는 댓글은 손대지 않는다. 정화 규칙 자체는 MarkdownBodySanitizerTest가 본다. */
@SpringBootTest
@Transactional
class PostBodySanitizeIntegrationTest {

    private static final String ATTACK =
            "<p>시즌권 후기</p>\n"
                    + "<script>alert(1)</script>"
                    + "<img src=\"https://example.com/a.jpg\" onerror=\"alert(1)\">\n\n"
                    + "[눌러](javascript:alert(1)) > 인용 아님, a < b\n\n```\n<script>코드 예시</script>\n```";
    private static final String CLEANED =
            "<p>시즌권 후기</p>\n"
                    + "&lt;script>alert(1)&lt;/script>"
                    + "<img src=\"https://example.com/a.jpg\">\n\n"
                    + "[눌러]&#40;javascript:alert(1)) > 인용 아님, a < b\n\n```\n<script>코드 예시</script>\n```";

    @Autowired private PostService postService;
    @Autowired private MarketListingService marketListingService;
    @Autowired private CommentService commentService;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private MarketCategoryRepository marketCategoryRepository;
    @Autowired private MarketListingRepository marketListingRepository;
    @Autowired private MemberRepository memberRepository;

    private CustomUserDetails writer;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString();
        Member member =
                memberRepository.save(
                        Member.builder()
                                .email(unique + "@test.invalid")
                                .nickname(unique.substring(0, 12))
                                .role(Role.ROLE_USER)
                                .build());
        writer = new CustomUserDetails(member);
        postCategory("FREE", "자유게시판");
        postCategory("MARKET", "중고장터");
        marketCategoryRepository
                .findByCode("SNOWBOARD")
                .orElseGet(
                        () ->
                                marketCategoryRepository.save(
                                        MarketCategory.builder()
                                                .code("SNOWBOARD")
                                                .name("스노보드")
                                                .active(true)
                                                .build()));
    }

    private void postCategory(String code, String name) {
        categoryRepository
                .findByCode(code)
                .orElseGet(
                        () ->
                                categoryRepository.save(
                                        PostCategory.builder().code(code).name(name).build()));
    }

    private PostResponse createPost(String content) {
        return postService.createPost(
                PostCreateRequest.builder()
                        .categoryCode("FREE")
                        .title("제목")
                        .content(content)
                        .isAnonymous(false)
                        .build(),
                writer,
                "127.0.0.1");
    }

    private String storedContent(String publicId) {
        return postRepository.findByPublicId(publicId).map(Post::getContent).orElseThrow();
    }

    @Test
    @DisplayName("글을 쓰면 위험한 HTML과 주소가 지워진 본문이 저장된다")
    void createPostStoresSanitizedBody() {
        PostResponse response = createPost(ATTACK);

        assertThat(storedContent(response.publicId())).isEqualTo(CLEANED);
    }

    @Test
    @DisplayName("글을 고칠 때도 같은 정화를 거친다")
    void updatePostStoresSanitizedBody() {
        PostResponse created = createPost("처음 본문");

        postService.updatePost(
                created.publicId(),
                PostUpdateRequest.builder()
                        .categoryCode("FREE")
                        .title("제목")
                        .content(ATTACK)
                        .build(),
                writer);

        assertThat(storedContent(created.publicId())).isEqualTo(CLEANED);
    }

    @Test
    @DisplayName("정화된 본문을 그대로 다시 저장하면 본문이 바뀌지 않아 글 버전이 오르지 않는다")
    void resavingSanitizedBodyKeepsContentVersion() {
        PostResponse created = createPost(ATTACK);
        Post post = postRepository.findByPublicId(created.publicId()).orElseThrow();
        long version = post.getContentVersion();

        postService.updatePost(
                created.publicId(),
                PostUpdateRequest.builder()
                        .categoryCode("FREE")
                        .title("제목")
                        .content(post.getContent())
                        .build(),
                writer);

        assertThat(post.getContent()).isEqualTo(CLEANED);
        assertThat(post.getContentVersion()).isEqualTo(version);
    }

    @Test
    @DisplayName("판매글 설명은 쓸 때와 고칠 때 모두 정화한다")
    void marketListingStoresSanitizedBody() {
        String publicId =
                marketListingService
                        .create(
                                new MarketListingCreateRequest(
                                        "SNOWBOARD",
                                        "데크 팝니다",
                                        ATTACK,
                                        ProductCondition.GOOD,
                                        TransactionMethod.DIRECT,
                                        100_000L,
                                        false,
                                        false,
                                        "쪽지 주세요",
                                        List.of()),
                                writer,
                                "127.0.0.1")
                        .publicId();
        assertThat(storedContent(publicId)).isEqualTo(CLEANED);

        long version =
                marketListingRepository.findAll().stream()
                        .filter(listing -> listing.getPost().getPublicId().equals(publicId))
                        .findFirst()
                        .orElseThrow()
                        .getVersion();
        marketListingService.update(
                publicId,
                new MarketListingUpdateRequest(
                        version,
                        "SNOWBOARD",
                        "데크 팝니다",
                        "고친 설명<iframe src=\"https://evil.example\"></iframe>",
                        ProductCondition.GOOD,
                        TransactionMethod.DIRECT,
                        100_000L,
                        false,
                        false,
                        "쪽지 주세요",
                        List.of()),
                writer);
        assertThat(storedContent(publicId))
                .isEqualTo("고친 설명&lt;iframe src=\"https://evil.example\">&lt;/iframe>");
    }

    @Test
    @DisplayName("댓글은 화면에서 글자 그대로 보이므로 바꾸지 않는다")
    void commentBodyIsStoredAsWritten() {
        PostResponse post = createPost("본문");
        String comment = "<script>alert(1)</script> 는 이렇게 쓰고 a < b & c";

        CommentResponse response =
                commentService.createComment(
                        post.publicId(),
                        new CommentCreateRequest(null, comment, false, null),
                        writer,
                        "127.0.0.1");

        assertThat(response.content()).isEqualTo(comment);
    }
}
