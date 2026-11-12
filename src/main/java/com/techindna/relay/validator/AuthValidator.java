package com.techindna.relay.validator;

import com.techindna.relay.dto.RegisterInput;
import com.techindna.relay.exception.http.UnprocessableContentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthValidator {

    private final DataValidator dataValidator;

    public void validateRegistration(RegisterInput input) {
        String username = dataValidator.normalizeUsername(input.username());
        dataValidator.checkNullData("username", username);
        dataValidator.checkStringLength("username", username, 50);
        dataValidator.validateUsername(username);

        dataValidator.checkPasswordSecurityLevel("password", input.password(), "Password");
        dataValidator.checkNullData("confirmPassword", input.confirmPassword());
        if (!input.password().equals(input.confirmPassword())) {
            throw new UnprocessableContentException("Passwords do not match");
        }

        String firstName = dataValidator.normalizeName(input.firstName());
        dataValidator.checkNullData("firstName", firstName);
        dataValidator.checkStringLength("firstName", firstName, 100);
        dataValidator.validateName("firstName", firstName);

        String lastName = dataValidator.normalizeName(input.lastName());
        dataValidator.checkNullData("lastName", lastName);
        dataValidator.checkStringLength("lastName", lastName, 100);
        dataValidator.validateName("lastName", lastName);

        String email = dataValidator.normalizeEmail(input.email());
        dataValidator.checkNullData("email", email);
        dataValidator.checkStringLength("email", email, 100);
        dataValidator.validateEmail(email);
    }
}
