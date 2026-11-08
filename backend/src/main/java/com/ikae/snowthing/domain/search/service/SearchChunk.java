package com.ikae.snowthing.domain.search.service;

/**
 * 정규화가 끝난 구간 하나.
 *
 * @param chunkNo 게시글은 0이 제목, 1부터 본문. 댓글은 항상 0
 * @param text 정규화한 구간 텍스트 (해시 대상)
 * @param hash text의 SHA-256 (소문자 hex 64자)
 */
public record SearchChunk(int chunkNo, String text, String hash) {}
