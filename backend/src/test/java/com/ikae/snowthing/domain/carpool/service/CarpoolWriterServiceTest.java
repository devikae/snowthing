package com.ikae.snowthing.domain.carpool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.repository.CarpoolDetailRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class CarpoolWriterServiceTest {

    private static final String OWNER_PUBLIC_ID = "owner-public-id";
    private static final String OTHER_PUBLIC_ID = "other-public-id";

    private final PostRepository postRepository = mock(PostRepository.class);
    private final CarpoolDetailRepository carpoolDetailRepository =
            mock(CarpoolDetailRepository.class);
    private CarpoolWriterService service;
    private Post post;

    @BeforeEach
    void setUp() {
        service =
                new CarpoolWriterService(
                        postRepository,
                        mock(PostCategoryRepository.class),
                        mock(MemberRepository.class),
                        mock(ResortRepository.class),
                        carpoolDetailRepository);
        Member owner =
                Member.builder()
                        .publicId(OWNER_PUBLIC_ID)
                        .email("owner@example.com")
                        .nickname("owner")
                        .build();
        PostCategory category = PostCategory.builder().name("카풀").code("CARPOOL").build();
        post =
                Post.builder()
                        .member(owner)
                        .category(category)
                        .title("title")
                        .content("content")
                        .writerIp("127.0.0.1")
                        .build();
        when(postRepository.findWithMemberAndCategoryByPublicId(post.getPublicId()))
                .thenReturn(Optional.of(post));
        when(carpoolDetailRepository.findByPostPublicId(post.getPublicId()))
                .thenReturn(Optional.of(mock(CarpoolDetail.class)));
    }

    @Test
    void ownerCanSoftDeleteCarpoolPost() {
        service.delete(post.getPublicId(), OWNER_PUBLIC_ID, false);

        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
        assertThat(post.getDeletedAt()).isNotNull();
    }

    @Test
    void nonOwnerCannotDeleteCarpoolPost() {
        assertThatThrownBy(() -> service.delete(post.getPublicId(), OTHER_PUBLIC_ID, false))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        assertThat(post.isDeleted()).isFalse();
    }

    @Test
    void nonOwnerCannotUpdateCarpoolPost() {
        assertThatThrownBy(
                        () ->
                                service.update(
                                        post.getPublicId(), null, OTHER_PUBLIC_ID, false, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void adminCanSoftDeleteOtherMembersCarpoolPost() {
        service.delete(post.getPublicId(), OTHER_PUBLIC_ID, true);

        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
    }
}
