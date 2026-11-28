package com.techindna.relay.service.auth;

import com.techindna.relay.dto.ChangePasswordInput;
import com.techindna.relay.dto.MessageBody;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.exception.http.ForbiddenException;
import com.techindna.relay.exception.http.UnauthorizedException;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.service.event.HostEventService;
import com.techindna.relay.service.event.auth.PasswordChangeNotificationService;
import com.techindna.relay.validator.AuthValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangePasswordService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";

    private final AuthValidator authValidator;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final HostEventService hostEventService;
    private final PasswordChangeNotificationService passwordChangeNotificationService;

    @Transactional(noRollbackFor = {ForbiddenException.class, UnauthorizedException.class})
    public MessageBody changePassword(
            ChangePasswordInput input, Authentication authentication, HttpServletRequest request) {
        authValidator.validateChangePassword(input);

        JUser user = userRepository.findById(UUID.fromString(authentication.getName()))
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));

        JHost host = hostEventService.recordHost(user, request);

        if (!passwordEncoder.matches(input.oldPassword(), user.getPassword())) {
            hostEventService.logEvent(
                    host, EventLogSeverity.MEDIUM, "[PASSWORD]: Operation failed due to invalid credentials", request);
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        hostEventService.rejectLockedAccount(user, host, EventLogSeverity.MEDIUM,
                "[PASSWORD]: Operation failed due to locked account",
                "This account is locked and cannot change its password.", request);
        hostEventService.rejectUnverifiedAccount(user, host, EventLogSeverity.MEDIUM,
                "[PASSWORD]: Operation failed due to unverified account",
                "Account has not been verified", request);
        hostEventService.rejectBannedHost(host, EventLogSeverity.MEDIUM,
                "[PASSWORD]: Operation failed due to banned host", request);

        user.setPassword(passwordEncoder.encode(input.newPassword()));
        userRepository.saveAndFlush(user);

        hostEventService.logEvent(host, EventLogSeverity.INFO, "[PASSWORD]: Password changed", request);

        passwordChangeNotificationService.sendPasswordChangeNotification(user, request);

        return new MessageBody("Password changed successfully");
    }
}
