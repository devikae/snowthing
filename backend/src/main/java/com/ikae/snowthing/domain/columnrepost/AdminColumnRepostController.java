package com.ikae.snowthing.domain.columnrepost;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * 관리자가 다음 칼럼을 바로 올린다. 첫 글을 게시 시각 전에 올리거나, 실패로 멈춘 글을 고친 뒤 다시 올릴 때 쓴다.
 *
 * <p>스케줄러와 같은 서비스를 부르므로 순서, 중복 방지, 알림이 같다. 스케줄러가 꺼져 있어도 동작한다. 권한은 /api/v1/admin/** 규칙(ROLE_ADMIN)을
 * 따른다.
 */
@RestController
@RequestMapping("/api/v1/admin/column-repost")
@RequiredArgsConstructor
public class AdminColumnRepostController {

    private final ColumnRepostService service;

    public record PublishResponse(ColumnRepostService.Result result, long pending) {}

    @PostMapping("/publish-next")
    public ResponseEntity<PublishResponse> publishNext() {
        ColumnRepostService.Result result = service.publishNext();
        return ResponseEntity.ok(new PublishResponse(result, service.pendingCount()));
    }
}
