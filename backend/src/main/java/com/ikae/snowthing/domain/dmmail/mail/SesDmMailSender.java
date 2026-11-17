package com.ikae.snowthing.domain.dmmail.mail;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.UnknownHostException;

import com.ikae.snowthing.domain.dmmail.config.DmMailProperties;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.SesV2ClientBuilder;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

/**
 * SES로 쪽지 도착 메일을 보낸다.
 *
 * <p>SDK 자동 재시도를 끈 전용 클라이언트를 쓴다. SDK가 몰래 다시 보내면 수락 후 응답이 사라진 경우 앱이 모르는 중복이 생긴다. 재시도 여부는 워커가 상태 머신으로
 * 정한다. 인증메일 클라이언트의 설정은 바꾸지 않는다.
 *
 * <p>결과 분류 기준은 "요청이 SES에 닿지 않았다고 확신할 수 있는가"다.
 *
 * <ul>
 *   <li>HTTP 응답을 받음: 429는 명시적 재시도 가능 거절, 그 밖의 4xx는 영구 거절, 5xx는 처리 중 실패인지 알 수 없어 결과 불명
 *   <li>응답을 받지 못함: 연결 자체가 맺어지지 않은 경우(연결 거부, 주소 해석 실패, 연결 제한 시간)만 미발송으로 본다. 응답 대기 중 제한 시간, 연결 끊김은
 *       요청이 이미 닿았을 수 있어 결과 불명이다
 * </ul>
 */
@Slf4j
public class SesDmMailSender implements DmMailSender {

    private final SesV2Client client;
    private final String from;
    private final String configurationSet;

    public SesDmMailSender(
            SesV2Client client, String fromAddress, String fromName, String configurationSet) {
        this.client = client;
        this.from = fromName + " <" + fromAddress + ">";
        this.configurationSet = configurationSet == null ? "" : configurationSet;
    }

    /** 재시도를 끄고 제한 시간을 건 클라이언트를 만든다. credentials가 null이면 SDK 기본 체인을 쓴다. */
    public static SesV2Client createClient(
            String region, DmMailProperties.Ses ses, AwsCredentialsProvider credentials) {
        SesV2ClientBuilder builder =
                SesV2Client.builder()
                        .region(Region.of(region))
                        .overrideConfiguration(
                                ClientOverrideConfiguration.builder()
                                        .retryStrategy(AwsRetryStrategy.doNotRetry())
                                        .apiCallAttemptTimeout(ses.attemptTimeout())
                                        .apiCallTimeout(ses.callTimeout())
                                        .build());
        if (!ses.endpoint().isEmpty()) {
            builder.endpointOverride(URI.create(ses.endpoint()));
        }
        if (credentials != null) {
            builder.credentialsProvider(credentials);
        }
        return builder.build();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public DmMailResult send(DmMail mail) {
        SendEmailRequest.Builder request =
                SendEmailRequest.builder()
                        .fromEmailAddress(from)
                        .destination(Destination.builder().toAddresses(mail.to()).build())
                        .content(
                                EmailContent.builder()
                                        .simple(
                                                Message.builder()
                                                        .subject(utf8(mail.subject()))
                                                        .body(
                                                                Body.builder()
                                                                        .text(utf8(mail.text()))
                                                                        .html(utf8(mail.html()))
                                                                        .build())
                                                        .build())
                                        .build());
        if (!configurationSet.isBlank()) {
            request.configurationSetName(configurationSet);
        }
        try {
            return new DmMailResult.Accepted(client.sendEmail(request.build()).messageId());
        } catch (SesV2Exception exception) {
            return classifyResponse(exception);
        } catch (SdkException exception) {
            return classifyNoResponse(exception);
        }
    }

    private static DmMailResult classifyResponse(SesV2Exception exception) {
        int status = exception.statusCode();
        String awsCode =
                exception.awsErrorDetails() != null
                                && exception.awsErrorDetails().errorCode() != null
                        ? exception.awsErrorDetails().errorCode()
                        : "HTTP_" + status;
        String code = truncate("SES_" + status + "_" + awsCode);
        if (status == 429) {
            return new DmMailResult.NotSent(code);
        }
        if (status >= 400 && status < 500) {
            return new DmMailResult.Rejected(code);
        }
        return new DmMailResult.Unknown(code);
    }

    private static DmMailResult classifyNoResponse(SdkException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConnectException
                    || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException
                    || cause.getClass().getSimpleName().equals("ConnectTimeoutException")) {
                return new DmMailResult.NotSent(
                        truncate("CONNECT_" + cause.getClass().getSimpleName()));
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return new DmMailResult.Unknown(truncate("NO_RESPONSE_" + rootName(exception)));
    }

    private static String rootName(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    private static Content utf8(String data) {
        return Content.builder().data(data).charset("UTF-8").build();
    }

    private static String truncate(String code) {
        return code.length() > 100 ? code.substring(0, 100) : code;
    }
}
