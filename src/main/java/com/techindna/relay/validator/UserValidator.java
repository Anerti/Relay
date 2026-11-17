package com.techindna.relay.validator;

import com.techindna.relay.dto.UpdateUserInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserValidator {

    private final DataValidator dataValidator;

    public UpdateUserInput validateUpdate(UpdateUserInput input) {
        String username = dataValidator.normalizeUsername(input.username());
        dataValidator.checkStringLength("username", username, 50);
        dataValidator.validateUsername(username);

        String firstName = dataValidator.normalizeName(input.firstName());
        dataValidator.checkStringLength("firstName", firstName, 100);
        dataValidator.validateName("firstName", firstName);

        String lastName = dataValidator.normalizeName(input.lastName());
        dataValidator.checkStringLength("lastName", lastName, 100);
        dataValidator.validateName("lastName", lastName);

        return new UpdateUserInput(username, firstName, lastName);
    }
}
