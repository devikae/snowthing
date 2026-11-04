package com.ikae.snowthing.domain.search.semantic;

/** 임베딩 제공자 호출 실패. 메시지에 요청 본문(원문)이나 키를 넣지 않는다. */
public class EmbeddingProviderException extends RuntimeException {

    public EmbeddingProviderException(String message) {
        super(message);
    }

    public EmbeddingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
