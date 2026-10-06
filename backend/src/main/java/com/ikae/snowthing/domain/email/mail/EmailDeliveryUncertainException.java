package com.ikae.snowthing.domain.email.mail;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

public class EmailDeliveryUncertainException extends CustomException {

    public EmailDeliveryUncertainException(Throwable cause) {
        super(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE, cause);
    }
}
