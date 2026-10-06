package com.ikae.snowthing.domain.email.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

@Component
@ConditionalOnProperty(name = "snowthing.mail.provider", havingValue = "ses")
@Slf4j
public class SesVerificationEmailSender implements VerificationEmailSender {

    private final SesV2Client sesClient;
    private final String fromAddress;
    private final String fromName;
    private final String configurationSet;

    public SesVerificationEmailSender(
            SesV2Client sesClient,
            @Value("${snowthing.mail.from-address}") String fromAddress,
            @Value("${snowthing.mail.from-name:SnowThing}") String fromName,
            @Value("${snowthing.mail.configuration-set:}") String configurationSet) {
        this.sesClient = sesClient;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.configurationSet = configurationSet;
    }

    @Override
    public String sendVerificationCode(
            String recipient, String verificationCode, EmailVerificationPurpose purpose) {
        String action = purpose == EmailVerificationPurpose.SIGN_UP ? "회원가입" : "비밀번호 재설정";
        String text = "SnowThing " + action + " 인증번호는 " + verificationCode + "입니다. 5분 안에 입력해 주세요.";
        String html =
                "<h2>SnowThing "
                        + action
                        + "</h2><p>인증번호는 <strong>"
                        + verificationCode
                        + "</strong>입니다.</p><p>5분 안에 입력해 주세요.</p>";
        SendEmailRequest.Builder request =
                SendEmailRequest.builder()
                        .fromEmailAddress(fromName + " <" + fromAddress + ">")
                        .destination(Destination.builder().toAddresses(recipient).build())
                        .content(
                                EmailContent.builder()
                                        .simple(
                                                Message.builder()
                                                        .subject(
                                                                Content.builder()
                                                                        .data(
                                                                                "[SnowThing] "
                                                                                        + action
                                                                                        + " 인증번호")
                                                                        .charset("UTF-8")
                                                                        .build())
                                                        .body(
                                                                Body.builder()
                                                                        .text(
                                                                                Content.builder()
                                                                                        .data(text)
                                                                                        .charset(
                                                                                                "UTF-8")
                                                                                        .build())
                                                                        .html(
                                                                                Content.builder()
                                                                                        .data(html)
                                                                                        .charset(
                                                                                                "UTF-8")
                                                                                        .build())
                                                                        .build())
                                                        .build())
                                        .build());
        if (!configurationSet.isBlank()) {
            request.configurationSetName(configurationSet);
        }
        try {
            return sesClient.sendEmail(request.build()).messageId();
        } catch (SesV2Exception exception) {
            String awsErrorCode =
                    exception.awsErrorDetails() != null
                            ? exception.awsErrorDetails().errorCode()
                            : "unknown";
            log.warn(
                    "SES verification email service failure: status={}, awsErrorCode={}",
                    exception.statusCode(),
                    awsErrorCode);
            throw new CustomException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        } catch (SdkException exception) {
            log.warn(
                    "SES verification email result is uncertain: exceptionType={}",
                    exception.getClass().getSimpleName());
            throw new EmailDeliveryUncertainException(exception);
        }
    }
}
