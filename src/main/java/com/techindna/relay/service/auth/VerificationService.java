package com.techindna.relay.service.auth;

import com.techindna.relay.config.ClientDataConfig;
import com.techindna.relay.dto.VerifyRegistrationResponse;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.entity.enums.HostStatus;
import com.techindna.relay.entity.enums.UserStatus;
import com.techindna.relay.exception.http.ForbiddenException;
import com.techindna.relay.exception.http.UnauthorizedException;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.jwt.JwtTokenProvider;
import com.techindna.relay.service.enums.VerificationFlow;
import com.techindna.relay.service.event.HostEventService;
import com.techindna.relay.service.mapper.UserMapper;
import com.techindna.relay.service.redis.VerificationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";
    private static final String ACCOUNT_LOCKED_OR_INACTIVE_MESSAGE =
            "Account is locked or inactive. Please request account access restoration to continue.";
    private static final String ACCOUNT_NOT_VERIFIED_MESSAGE =
            "Verify your email address before signing in.";

    private final VerificationTokenService verificationTokenService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final HostEventService hostEventService;
    private final JwtTokenProvider jwtTokenProvider;
    private final ClientDataConfig clientDataConfig;

    public String confirmationFlow(String token) {
        try {
            return verificationTokenService.findFlow(token) == VerificationFlow.LOGIN ? "login" : "register";
        } catch (DataAccessException e) {
            log.warn("Could not read the verification token flow, defaulting to registration", e);
            return "register";
        }
    }

    @Transactional
    public VerifyRegistrationResponse confirmRegistration(String token, HttpServletRequest request) {
        UUID userId = verificationTokenService.consume(token, VerificationFlow.REGISTER);
        if (userId == null) {
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }

        JUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN_MESSAGE));

        user.setVerified(true);
        user = userRepository.saveAndFlush(user);

        JHost host = hostEventService.recordHost(user, request);
        hostEventService.logEvent(host, EventLogSeverity.INFO, "[REGISTRATION]: Account verified", request);

        String jwt = jwtTokenProvider.generateToken(
                user.getId().toString(), user.getRole().name(), clientDataConfig.ipAddress(request));

        return new VerifyRegistrationResponse(jwt, userMapper.toDomain(user));
    }

    @Transactional(noRollbackFor = {ForbiddenException.class, UnauthorizedException.class})
    public VerifyRegistrationResponse confirmLogin(String token, HttpServletRequest request) {
        UUID userId = verificationTokenService.consume(token, VerificationFlow.LOGIN);
        if (userId == null) {
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }

        JUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN_MESSAGE));

        JHost host = hostEventService.recordHost(user, request);
        assertSignInAllowed(user, host, request);

        hostEventService.logEvent(
                host, EventLogSeverity.INFO, "[LOGIN]: Account signed in", request);

        String jwt = jwtTokenProvider.generateToken(
                user.getId().toString(), user.getRole().name(), clientDataConfig.ipAddress(request));

        return new VerifyRegistrationResponse(jwt, userMapper.toDomain(user));
    }

    private void assertSignInAllowed(JUser user, JHost host, HttpServletRequest request) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            hostEventService.logEvent(
                    host, EventLogSeverity.MEDIUM, "[LOGIN FAILED]: account locked or inactive", request);
            throw new ForbiddenException(ACCOUNT_LOCKED_OR_INACTIVE_MESSAGE);
        }
        if (!Boolean.TRUE.equals(user.getVerified())) {
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
