package com.ikae.snowthing.domain.image;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ikae.snowthing.domain.image.dto.ImageUploadResponse;
import com.ikae.snowthing.domain.image.entity.ProfileImageCleanup;
import com.ikae.snowthing.domain.image.repository.ProfileImageCleanupRepository;
import com.ikae.snowthing.domain.image.service.*;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@SpringBootTest
class ProfileImageLifecycleIntegrationTest {
    @Autowired ProfileImageLifecycleService lifecycle;
    @Autowired ProfileImageCleanupRepository jobs;
    @Autowired MemberRepository members;
    @MockitoBean ImageService images;
    private final String email = "image-lifecycle@example.test";
    private final String oldKey = "public/profiles/11111111-1111-1111-1111-111111111111.jpg";
    private final String prefix = "https://images.snowthing.org/";

    @BeforeEach
    void setup() {
        Member member = Member.builder().email(email).nickname("이미지수명").build();
        member.updateProfile(member.getNickname(), null, null, prefix + oldKey);
        members.saveAndFlush(member);
        when(images.profileUrl(anyString())).thenAnswer(i -> prefix + i.getArgument(0));
        when(images.profileKey(any()))
                .thenAnswer(
                        i -> {
                            String url = i.getArgument(0);
                            return url == null
                                    ? Optional.empty()
                                    : Optional.of(url.substring(prefix.length()));
                        });
        when(images.uploadProfileImage(any(), anyString()))
                .thenAnswer(
                        i -> new ImageUploadResponse(prefix + i.getArgument(1), i.getArgument(1)));
    }

    @AfterEach
    void cleanup() {
        members.findByEmail(email).ifPresent(members::delete);
        jobs.deleteAll();
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1});
    }

    @Test
    void replacementQueuesOldPhotoOnlyAfterNewReferenceCommits() {
        var uploaded = lifecycle.upload(email, file());
        assertThat(members.findByEmail(email).orElseThrow().getProfileImageUrl())
                .isEqualTo(uploaded.imageUrl());
        assertThat(jobs.findById(uploaded.imageKey())).isEmpty();
        assertThat(jobs.findById(oldKey)).isPresent();
        lifecycle.cleanOne(oldKey);
        verify(images).deleteProfileImage(oldKey);
        assertThat(jobs.findById(oldKey)).isEmpty();
    }

    @Test
    void resetQueuesDeletionAndFailedStorageCleanupRemainsRetryable() {
        lifecycle.reset(email);
        doThrow(new CustomException(ErrorCode.FILE_UPLOAD_FAILED))
                .when(images)
                .deleteProfileImage(oldKey);
        lifecycle.cleanOne(oldKey);
        var retry = jobs.findById(oldKey).orElseThrow();
        assertThat(retry.getAttempts()).isEqualTo(1);
        assertThat(retry.getNotBefore()).isAfter(Instant.now());
        assertThat(members.findByEmail(email).orElseThrow().getProfileImageUrl()).isNull();
    }

    @Test
    void failedMemberAttachmentCompensatesNewUploadAndPreservesOldPhoto() {
        assertThatThrownBy(() -> lifecycle.upload("missing@example.test", file()))
                .isInstanceOf(CustomException.class);
        var deleted = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(images).deleteProfileImage(deleted.capture());
        assertThat(deleted.getValue()).isNotEqualTo(oldKey);
        assertThat(members.findByEmail(email).orElseThrow().getProfileImageUrl())
                .isEqualTo(prefix + oldKey);
        assertThat(jobs.findById(oldKey)).isEmpty();
    }

    @Test
    void cleanupNeverDeletesAnActivelyReferencedObject() {
        jobs.saveAndFlush(
                new ProfileImageCleanup(oldKey, prefix + oldKey, Instant.now().minusSeconds(1)));
        lifecycle.cleanOne(oldKey);
        verify(images, never()).deleteProfileImage(any());
    }

    @Test
    void orphanUploadJournalSurvivesProcessReplacement() {
        String key = "public/profiles/" + UUID.randomUUID() + ".jpg";
        jobs.saveAndFlush(
                new ProfileImageCleanup(key, prefix + key, Instant.now().minusSeconds(1)));
        lifecycle.cleanup();
        verify(images).deleteProfileImage(key);
        assertThat(jobs.findById(key)).isEmpty();
    }
}
