package com.ikae.snowthing.domain.resortweather.source;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ikae.snowthing.domain.resortweather.config.ResortWeatherProperties;

/** 기온 소스 공용 HTTP 호출. 짧은 타임아웃으로 외부 사이트 지연이 요청 스레드를 오래 잡지 않게 한다. */
@Component
public class WeatherHttpClient {

    static final String USER_AGENT =
            "Mozilla/5.0 (compatible; SnowThingBot/1.0; +https://snowthing.org)";

    private final RestClient client;

    @Autowired
    public WeatherHttpClient(ResortWeatherProperties properties) {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofMillis(properties.effectiveConnectTimeoutMillis()))
                        .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.effectiveReadTimeoutMillis()));
        this.client =
                RestClient.builder()
                        .requestFactory(requestFactory)
                        .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                        .build();
    }

    WeatherHttpClient(RestClient client) {
        this.client = client;
    }

    /** 실패 원인은 예외 종류만 남긴다. 기상청 URL에는 서비스 키가 들어 있어 메시지를 옮기지 않는다. */
    public byte[] get(URI uri) {
        try {
            byte[] body = client.get().uri(uri).retrieve().body(byte[].class);
            if (body == null || body.length == 0) {
                throw new WeatherFetchException("응답 본문이 비어 있습니다.");
            }
            return body;
        } catch (WeatherFetchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WeatherFetchException("외부 호출 실패: " + exception.getClass().getSimpleName());
        }
    }
}
