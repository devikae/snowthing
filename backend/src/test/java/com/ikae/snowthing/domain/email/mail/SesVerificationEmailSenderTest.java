package com.ikae.snowthing.domain.email.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.global.error.ErrorCode;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

class SesVerificationEmailSenderTest {

    @Test
    void sendsUtf8TextAndHtmlWithConfiguredSenderAndConfigurationSet() {
        SesV2Client client = mock(SesV2Client.class);
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        given(client.sendEmail(captor.capture()))
                .willReturn(SendEmailResponse.builder().messageId("ses-message-id").build());
        SesVerificationEmailSender sender =
                new SesVerificationEmailSender(
                        client, "help@snowthing.org", "SnowThing", "snowthing-mail-transactional");

        String messageId =
                sender.sendVerificationCode(
                        "member@example.com", "012345", EmailVerificationPurpose.SIGN_UP);

        SendEmailRequest request = captor.getValue();
        assertThat(messageId).isEqualTo("ses-message-id");
        assertThat(request.fromEmailAddress()).isEqualTo("SnowThing <help@snowthing.org>");
        assertThat(request.configurationSetName()).isEqualTo("snowthing-mail-transactional");
        assertThat(request.destination().toAddresses()).containsExactly("member@example.com");
        assertThat(request.content().simple().body().text().data()).contains("012345");
        assertThat(request.content().simple().body().html().data()).contains("012345");
    }

    @Test
    void sdkFailureIsMappedToPublicDeliveryErrorWithoutLeakingAwsDetails() {
        SesV2Client client = mock(SesV2Client.class);
        given(client.sendEmail(any(SendEmailRequest.class)))
                .willThrow(SdkClientException.create("private aws detail"));
        SesVerificationEmailSender sender =
                new SesVerificationEmailSender(client, "help@snowthing.org", "SnowThing", "");

        assertThatThrownBy(
                        () ->
                                sender.sendVerificationCode(
                                        "member@example.com",
                                        "012345",
                                        EmailVerificationPurpose.PASSWORD_RESET))
                .isInstanceOf(EmailDeliveryUncertainException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
    }
}
