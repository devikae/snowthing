package com.ikae.snowthing.domain.slopestatus.source;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ikae.snowthing.domain.slopestatus.config.SlopeStatusProperties;

/**
 * 현황 페이지 호출. 스케줄러 스레드에서만 부르므로 기온보다 타임아웃을 넉넉히 둔다. User-Agent로 서비스를 밝힌다. 리다이렉트는 따르지 않는다(고정 URL이 다른
 * 곳으로 옮겨 가면 실패로 보고 사람이 확인한다).
 */
@Component
public class SlopeStatusHttpClient {

    static final String USER_AGENT =
            "Mozilla/5.0 (compatible; SnowThingBot/1.0; +https://snowthing.org)";

    private final RestClient client;

    @Autowired
    public SlopeStatusHttpClient(SlopeStatusProperties properties) {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofMillis(properties.effectiveConnectTimeoutMillis()))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.effectiveReadTimeoutMillis()));
        this.client =
                RestClient.builder()
                        .requestFactory(requestFactory)
                        .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                        .build();
    }

    /** 실패 원인은 예외 종류만 남긴다. */
    public byte[] get(URI uri) {
        try {
            byte[] body = client.get().uri(uri).retrieve().body(byte[].class);
            if (body == null || body.length == 0) {
                throw new SlopeFetchException("응답 본문이 비어 있습니다.");
            }
            return body;
        } catch (SlopeFetchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SlopeFetchException("외부 호출 실패: " + exception.getClass().getSimpleName());
        }
    }
}
