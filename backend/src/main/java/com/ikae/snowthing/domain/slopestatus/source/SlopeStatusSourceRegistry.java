package com.ikae.snowthing.domain.slopestatus.source;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Component;

/**
 * 리조트 코드별 공식 현황 출처. URL은 코드 상수다(요청 값으로 URL을 만들지 않는다). 수집할 수 있는 곳만 둔다. 나머지 리조트를 뺀 이유는
 * docs/conception/030_slope_status/01_design.md 출처 조사 표에 있다.
 */
@Component
public class SlopeStatusSourceRegistry {

    /**
     * @param fetchUri 서버가 읽는 주소
     * @param pageUrl 사용자에게 보여 줄 공식 페이지 주소. 휘닉스처럼 JSON을 읽는 곳은 그 JSON을 쓰는 화면 주소다
     */
    public record SlopeSource(
            URI fetchUri, String pageUrl, Function<byte[], SlopeSnapshot> parser) {}

    private static final Map<String, SlopeSource> SOURCES =
            Map.of(
                    "HIGH1",
                    page(
                            "https://www.high1.com/ski/slopeView.do?key=748&mode=p",
                            SlopeStatusParsers::high1),
                    "YONGPYONG",
                    page(
                            "https://www.yongpyong.co.kr/kor/skiNboard/slope/openStatusBoard.do",
                            SlopeStatusParsers::yongpyong),
                    "KONJIAM",
                    page(
                            "https://www.konjiamresort.co.kr/ski/slopeOpenClose.dev",
                            SlopeStatusParsers::konjiam),
                    "PHOENIX",
                    new SlopeSource(
                            URI.create("https://phoenixhnr.co.kr/api/snowpark/slope"),
                            "https://phoenixhnr.co.kr/static/pyeongchang/snowpark/slope-lift",
                            SlopeStatusParsers::phoenix));

    private static SlopeSource page(String url, Function<byte[], SlopeSnapshot> parser) {
        return new SlopeSource(URI.create(url), url, parser);
    }

    public Optional<SlopeSource> sourceFor(String resortCode) {
        return Optional.ofNullable(SOURCES.get(resortCode));
    }
}
