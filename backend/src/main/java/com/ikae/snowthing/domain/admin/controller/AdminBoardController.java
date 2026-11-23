package com.ikae.snowthing.domain.admin.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.AdminBoardRequest;
import com.ikae.snowthing.domain.admin.dto.AdminBoardResponse;
import com.ikae.snowthing.domain.admin.service.BoardService;
import com.ikae.snowthing.domain.post.entity.BoardStatus;

import lombok.RequiredArgsConstructor;

/** 관리자 게시판 관리 (IKA-114). /api/v1/admin/** 이라 관리자만 부른다. 게시판은 코드로 가리킨다. */
@RestController
@RequestMapping("/api/v1/admin/boards")
@RequiredArgsConstructor
@Validated
public class AdminBoardController {

    private final BoardService boardService;

    @GetMapping
    public ResponseEntity<List<AdminBoardResponse>> getBoards() {
        return ResponseEntity.ok(boardService.getAdminBoards());
    }

    @PostMapping
    public ResponseEntity<AdminBoardResponse> create(
            @Valid @RequestBody AdminBoardRequest request) {
        return ResponseEntity.ok(boardService.create(request));
    }

    @PatchMapping("/{code}")
    public ResponseEntity<AdminBoardResponse> update(
            @PathVariable("code") String code, @Valid @RequestBody AdminBoardRequest request) {
        return ResponseEntity.ok(boardService.update(code, request));
    }

    @PatchMapping("/{code}/status")
    public ResponseEntity<AdminBoardResponse> changeStatus(
            @PathVariable("code") String code,
            @RequestParam("status") @NotNull BoardStatus status) {
        return ResponseEntity.ok(boardService.changeStatus(code, status));
    }

    @PostMapping("/{code}/move")
    public ResponseEntity<List<AdminBoardResponse>> move(
            @PathVariable("code") String code, @RequestParam("direction") String direction) {
        return ResponseEntity.ok(boardService.move(code, "UP".equalsIgnoreCase(direction)));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable("code") String code) {
        boardService.delete(code);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{code}/restore")
    public ResponseEntity<AdminBoardResponse> restore(@PathVariable("code") String code) {
        return ResponseEntity.ok(boardService.restore(code));
    }
}
