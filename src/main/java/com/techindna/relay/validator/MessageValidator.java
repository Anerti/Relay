package com.techindna.relay.validator;

import com.techindna.relay.dto.SendMessageInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageValidator {

    private final DataValidator dataValidator;

    public void validateSend(SendMessageInput input) {
        dataValidator.checkNullData("content", input.content());
        dataValidator.checkStringLength("content", input.content(), 4000);
    }
}
