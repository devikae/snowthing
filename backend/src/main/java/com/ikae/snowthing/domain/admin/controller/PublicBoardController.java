package com.ikae.snowthing.domain.admin.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.service.BoardService;
import com.ikae.snowthing.domain.post.dto.BoardResponse;

import lombok.RequiredArgsConstructor;

/** 열린 게시판 목록 (IKA-114). 프론트 서버가 60초 캐시로 받아 메뉴, 홈 탭, 글쓰기에 쓴다. */
@RestController
@RequestMapping("/api/v1/public/boards")
@RequiredArgsConstructor
public class PublicBoardController {

    private final BoardService boardService;

    @GetMapping
    public ResponseEntity<List<BoardResponse>> getBoards() {
        return ResponseEntity.ok(boardService.getOpenBoards());
    }
}
