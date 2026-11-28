package com.techindna.relay.validator;

import com.techindna.relay.dto.UpdateHostInput;
import com.techindna.relay.entity.enums.HostStatus;
import com.techindna.relay.exception.http.UnprocessableContentException;
import org.springframework.stereotype.Component;

@Component
public class HostValidator {

    public HostStatus validateUpdate(UpdateHostInput input) {
        return switch (input.status()) {
            case "authorized" -> HostStatus.AUTHORIZED;
            case "banned" -> HostStatus.BANNED;
            case null, default -> throw new UnprocessableContentException("status must be authorized or banned");
        };
    }
}
