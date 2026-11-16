package com.techindna.relay.service.auth;

import com.techindna.relay.config.ClientDataConfig;
import com.techindna.relay.dto.LoginInput;
import com.techindna.relay.dto.MessageBody;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.entity.enums.HostStatus;
import com.techindna.relay.entity.enums.UserStatus;
import com.techindna.relay.exception.http.ForbiddenException;
import com.techindna.relay.exception.http.UnauthorizedException;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.service.event.HostEventService;
import com.techindna.relay.service.event.auth.VerificationEmailService;
import com.techindna.relay.validator.AuthValidator;
import com.techindna.relay.validator.DataValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username/email or password.";
    private static final String ACCOUNT_LOCKED_MESSAGE =
            "This account has been locked after too many unsuccessful sign-in attempts.";
    private static final String ACCOUNT_NOT_VERIFIED_MESSAGE =
            "Verify your email address before signing in.";

    private final AuthValidator authValidator;
    private final DataValidator dataValidator;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final HostEventService hostEventService;
    private final VerificationEmailService verificationEmailService;
    private final ClientDataConfig clientDataConfig;

    @Transactional(noRollbackFor = {ForbiddenException.class, UnauthorizedException.class})
    public MessageBody login(LoginInput input, HttpServletRequest request) {
        authValidator.validateLogin(input);

        String username = dataValidator.normalizeUsername(input.username());
        String email = dataValidator.normalizeEmail(input.email());

        JUser user = (username != null && !username.isBlank()
                        ? userRepository.findByUsername(username)
                        : userRepository.findByEmail(email))
                .orElse(null);
        if (user == null) {
            passwordEncoder.encode(input.password());
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        JHost host = hostEventService.recordHost(user, request);

        if (!passwordEncoder.matches(input.password(), user.getPassword())) {
            hostEventService.logEvent(
                    host, EventLogSeverity.MEDIUM, "[LOGIN FAILED]: invalid credentials", request);
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        assertSignInAllowed(user, host, request);

        hostEventService.logEvent(
                host, EventLogSeverity.INFO, "[LOGIN]: Verification link requested", request);

        verificationEmailService.sendLoginVerification(user, request);

        return new MessageBody("A verification link has been sent to your email");
    }

    private void assertSignInAllowed(JUser user, JHost host, HttpServletRequest request) {
        if (user.getStatus() == UserStatus.LOCKED) {
            hostEventService.logEvent(
                    host, EventLogSeverity.MEDIUM, "[LOGIN FAILED]: account locked", request);
            throw new ForbiddenException(ACCOUNT_LOCKED_MESSAGE);
        }
        if (Boolean.FALSE.equals(user.getVerified())) {
            hostEventService.logEvent(
                    host, EventLogSeverity.MEDIUM, "[LOGIN FAILED]: account not verified", request);
            throw new ForbiddenException(ACCOUNT_NOT_VERIFIED_MESSAGE);
        }
        if (host.getStatus() == HostStatus.BANNED) {
            hostEventService.logEvent(
                    host, EventLogSeverity.MEDIUM, "[LOGIN FAILED]: host banned", request);
            throw new ForbiddenException(
                    "Host " + clientDataConfig.ipAddress(request) + " is banned from accessing this account");
        }
    }
}
