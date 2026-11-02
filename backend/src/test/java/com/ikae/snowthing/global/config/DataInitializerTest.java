package com.ikae.snowthing.global.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ikae.snowthing.domain.market.repository.MarketCategoryRepository;
import com.ikae.snowthing.domain.member.repository.*;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.*;

class DataInitializerTest {
    @Test
    void emptyAndPreviouslyPartialDatabasesReceiveEveryDefaultCategoryOnlyOnce() {
        for (boolean noticeAlreadyExists : List.of(false, true)) {
            var categories = new LinkedHashMap<String, PostCategory>();
            if (noticeAlreadyExists)
                categories.put(
                        "NOTICE", PostCategory.builder().code("NOTICE").name("공지사항").build());
            var members = mock(MemberRepository.class);
            when(members.existsByEmail(anyString())).thenReturn(true);
            var resorts = mock(ResortRepository.class);
            var styles = mock(RidingStyleRepository.class);
            when(styles.count()).thenReturn(1L);
            var categoryRepo = mock(PostCategoryRepository.class);
            when(categoryRepo.findByCode(anyString()))
                    .thenAnswer(i -> Optional.ofNullable(categories.get(i.getArgument(0))));
            when(categoryRepo.save(any()))
                    .thenAnswer(
                            i -> {
                                PostCategory c = i.getArgument(0);
                                assertThat(categories).doesNotContainKey(c.getCode());
                                categories.put(c.getCode(), c);
                                return c;
                            });
            var markets = mock(MarketCategoryRepository.class);
            when(markets.count()).thenReturn(1L);
            var posts = mock(PostRepository.class);
            when(posts.count()).thenReturn(1L);
            var initializer =
                    new DataInitializer(
                            members,
                            resorts,
                            styles,
                            categoryRepo,
                            markets,
                            posts,
                            mock(PasswordEncoder.class));
            initializer.run();
            initializer.run();
            assertThat(categories.keySet())
                    .contains("FREE", "ANONYMOUS", "QNA", "GEAR_VS", "FOOD", "CARPOOL", "NOTICE");
        }
    }
}
