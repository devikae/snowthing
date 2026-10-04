package com.ikae.snowthing.domain.carpool.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.carpool.dto.CarpoolCreateRequest;
import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelType;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;
import com.ikae.snowthing.domain.carpool.repository.CarpoolDetailRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;

class CarpoolServiceTest {

    private static final String OWNER_PUBLIC_ID = "owner-public-id";
    private static final String REQUESTER_PUBLIC_ID = "requester-public-id";

    @Test
    void rejectsPageIndexBeyondOneHundredPages() {
        PostRepository postRepository = mock(PostRepository.class);
        CarpoolDetailRepository carpoolDetailRepository = mock(CarpoolDetailRepository.class);
        CarpoolService service =
                new CarpoolService(
                        postRepository,
                        carpoolDetailRepository,
                        mock(CarpoolAutoCalculationService.class),
                        mock(CarpoolWriterService.class));

        assertThatThrownBy(() -> service.findPage(100, 20))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CARPOOL_PAGE_LIMIT_EXCEEDED);

        verifyNoInteractions(postRepository, carpoolDetailRepository);
    }

    @Test
    void rejectsNonOwnerBeforeCallingExternalCalculation() {
        PostRepository postRepository = mock(PostRepository.class);
        CarpoolAutoCalculationService calculationService =
                mock(CarpoolAutoCalculationService.class);
        CarpoolWriterService writerService = mock(CarpoolWriterService.class);
        CarpoolService service =
                new CarpoolService(
                        postRepository,
                        mock(CarpoolDetailRepository.class),
                        calculationService,
                        writerService);
        Post post = createPost(createMember(OWNER_PUBLIC_ID));
        when(postRepository.findWithMemberAndCategoryByPublicId(post.getPublicId()))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        post.getPublicId(),
                                        createRequest(),
                                        new CustomUserDetails(createMember(REQUESTER_PUBLIC_ID))))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verifyNoInteractions(calculationService, writerService);
    }

    private CarpoolCreateRequest createRequest() {
        return new CarpoolCreateRequest(
                "title",
                "content",
                "서울",
                "서울역",
                BigDecimal.valueOf(37.55),
                BigDecimal.valueOf(126.97),
                1L,
                CarpoolTripType.ONE_WAY,
                LocalDateTime.now().plusDays(1),
                null,
                2,
                CarpoolFuelType.GASOLINE,
                BigDecimal.valueOf(12),
                CarpoolCostMode.AUTO,
                null,
                true,
                null,
                false);
    }

    private Member createMember(String publicId) {
        return Member.builder()
                .publicId(publicId)
                .email(publicId + "@example.com")
                .nickname(publicId)
                .build();
    }

    private Post createPost(Member owner) {
        return Post.builder()
                .member(owner)
                .category(PostCategory.builder().name("카풀").code("CARPOOL").build())
                .title("title")
                .content("content")
                .writerIp("127.0.0.1")
                .build();
    }
}
