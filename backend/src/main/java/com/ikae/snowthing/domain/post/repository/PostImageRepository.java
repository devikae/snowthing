package com.ikae.snowthing.domain.post.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.post.entity.PostImage;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findByPostIdOrderBySortOrderAsc(Long postId);

    @Query(
            "SELECT pi FROM PostImage pi WHERE pi.post.id IN :postIds ORDER BY pi.post.id ASC, pi.sortOrder ASC")
    List<PostImage> findAllByPostIdsOrderBySortOrder(@Param("postIds") List<Long> postIds);

    boolean existsByImageUrlIn(Collection<String> imageUrls);

    // Native lookup deliberately includes recoverable, soft-deleted posts.
    @Query(
            value =
                    "SELECT EXISTS(SELECT 1 FROM post_content_image_reference WHERE image_key=:key)",
            nativeQuery = true)
    long countContentReference(@Param("key") String key);

    /**
     * 참조 표가 생기기 전 글의 본문에만 있는 사진을 찾는다. 본문 전체를 읽으므로 정리 작업에서 부르지 않고, 추적되지 않는 키를 올릴 때만 부른다.
     *
     * <p>숨긴 글도 복구될 수 있으므로 지워지지 않은 글은 상태와 상관없이 사용 중으로 본다.
     */
    @Query(
            value =
                    "SELECT EXISTS(SELECT 1 FROM post WHERE is_deleted = false AND post_id <> :postId"
                            + " AND LOCATE(:key, content) > 0)",
            nativeQuery = true)
    long countOtherLivePostBody(@Param("key") String key, @Param("postId") Long postId);
}
