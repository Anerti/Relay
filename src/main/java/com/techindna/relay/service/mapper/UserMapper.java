package com.techindna.relay.service.mapper;

import com.techindna.relay.dto.RegisterInput;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.validator.DataValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final PasswordEncoder passwordEncoder;
    private final DataValidator dataValidator;

    public JUser toPersistenceModel(RegisterInput input) {
        return JUser.builder()
                .username(dataValidator.normalizeUsername(input.username()))
                .password(passwordEncoder.encode(input.password()))
                .firstName(dataValidator.normalizeName(input.firstName()))
                .lastName(dataValidator.normalizeName(input.lastName()))
                .email(dataValidator.normalizeEmail(input.email()))
                .verified(false)
                .build();
    }
}
