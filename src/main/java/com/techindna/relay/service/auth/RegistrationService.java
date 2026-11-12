package com.techindna.relay.service.auth;

import com.techindna.relay.dto.MessageBody;
import com.techindna.relay.dto.RegisterInput;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.exception.http.ConflictException;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.service.event.HostEventService;
import com.techindna.relay.service.event.auth.VerificationEmailService;
import com.techindna.relay.service.mapper.UserMapper;
import com.techindna.relay.validator.AuthValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final String USERNAME_CONSTRAINT = "user_username_lower_key";
    private static final String EMAIL_CONSTRAINT = "user_email_lower_key";

    private final AuthValidator authValidator;
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final HostEventService hostEventService;
    private final VerificationEmailService verificationEmailService;

    @Transactional
    public MessageBody register(RegisterInput input, HttpServletRequest request) {
        authValidator.validateRegistration(input);

        JUser user;
        try {
            user = userRepository.saveAndFlush(userMapper.toPersistenceModel(input));
        } catch (DataIntegrityViolationException e) {
            String constraint = constraintName(e);
            if (USERNAME_CONSTRAINT.equalsIgnoreCase(constraint)) {
                throw new ConflictException("You cannot use this username");
            }
            if (EMAIL_CONSTRAINT.equalsIgnoreCase(constraint)) {
                throw new ConflictException("You cannot use this email address");
            }
            throw e;
        }

        JHost host = hostEventService.recordHost(user, request);
        hostEventService.logEvent(host, EventLogSeverity.INFO, "[REGISTRATION]: Account registered", request);

        verificationEmailService.sendRegistrationVerification(user, request);

        return new MessageBody("An email has been sent to verify your account");
    }

    private String constraintName(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
