package com.ikae.snowthing.domain.push.service;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/** 푸시 서비스로 보내는 HTTP POST 한 번. 테스트에서 실제 푸시 서비스 대신 끼워 넣으려고 나눴다. */
public interface PushHttpClient {

    /** 응답 상태 코드를 돌려준다. 연결 실패나 시간 초과는 예외로 던진다. */
    int post(URI uri, Map<String, String> headers, byte[] body)
            throws IOException, InterruptedException;
}
