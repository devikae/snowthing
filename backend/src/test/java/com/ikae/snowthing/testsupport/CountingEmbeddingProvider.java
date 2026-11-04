package com.ikae.snowthing.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ikae.snowthing.domain.search.semantic.EmbeddingProvider;
import com.ikae.snowthing.domain.search.semantic.EmbeddingProviderException;
import com.ikae.snowthing.domain.search.semantic.EmbeddingResult;
import com.ikae.snowthing.domain.search.semantic.HashingEmbeddingProvider;

/**
 * 테스트용 결정적 임베딩 제공자. 외부 호출 없이 {@link HashingEmbeddingProvider}로 벡터를 만들고 호출 수와 받은 텍스트를 센다. 다음 호출을 실패시킬 수
 * 있다. 입력 토큰은 글자 수로 흉내 낸다(비용 메타데이터 전달 확인용).
 */
public class CountingEmbeddingProvider implements EmbeddingProvider {

    private final HashingEmbeddingProvider delegate = new HashingEmbeddingProvider(64);
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> texts = new ArrayList<>();
    private final AtomicBoolean failNext = new AtomicBoolean();

    @Override
    public String modelId() {
        return "test-counting-" + delegate.modelId();
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }

    @Override
    public synchronized EmbeddingResult embed(List<String> input) {
        calls.incrementAndGet();
        if (failNext.getAndSet(false)) {
            throw new EmbeddingProviderException("test failure");
        }
        texts.addAll(input);
        long tokens = input.stream().mapToLong(String::length).sum();
        return new EmbeddingResult(delegate.embed(input).vectors(), tokens);
    }

    public int calls() {
        return calls.get();
    }

    public synchronized List<String> texts() {
        return List.copyOf(texts);
    }

    public void failNext() {
        failNext.set(true);
    }

    public synchronized void reset() {
        calls.set(0);
        texts.clear();
        failNext.set(false);
    }

    /** 테스트 컨텍스트에 이 제공자를 넣는다. 의미 검색 켜짐 여부는 각 테스트 클래스의 속성으로 정한다. */
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public CountingEmbeddingProvider countingEmbeddingProvider() {
            return new CountingEmbeddingProvider();
        }
    }
}
