package com.techindna.relay.service;

import com.techindna.relay.dto.PageResponse;
import com.techindna.relay.dto.UpdateUserInput;
import com.techindna.relay.entity.User;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.entity.enums.UserRole;
import com.techindna.relay.exception.http.ConflictException;
import com.techindna.relay.exception.http.ForbiddenException;
import com.techindna.relay.exception.http.NotFoundException;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.RBACRules;
import com.techindna.relay.service.event.AccountDeletionNotificationService;
import com.techindna.relay.service.event.HostEventService;
import com.techindna.relay.service.mapper.UserMapper;
import com.techindna.relay.validator.DataValidator;
import com.techindna.relay.validator.UserValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String SQL_STATE_VIOLATION = "23505";
    private static final String ACCOUNT_NOT_VERIFIED_MESSAGE =
            "Verify your email address before updating your profile.";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final DataValidator dataValidator;
    private final UserValidator userValidator;
    private final HostEventService hostEventService;
    private final AccountDeletionNotificationService accountDeletionNotificationService;

    @Transactional(readOnly = true)
    public User getUser(UUID userId, Authentication authentication) {
        JUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        RBACRules.authorize(
                RBACRules.roleOf(authentication),
                UUID.fromString(authentication.getName()),
                user.getRole(),
                user.getId());

        return userMapper.toDomain(user);
    }

    @Transactional(noRollbackFor = ForbiddenException.class)
    public User updateUser(
            UUID userId, UpdateUserInput input, Authentication authentication, HttpServletRequest request) {
        UpdateUserInput update = userValidator.validateUpdate(input);

        RBACRules.requireOwner(UUID.fromString(authentication.getName()), userId);

        JUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (Boolean.FALSE.equals(user.getVerified())) {
            hostEventService.logEvent(
                    hostEventService.recordHost(user, request),
                    EventLogSeverity.MEDIUM,
                    "[PROFILE]: Update failed due to unverified account",
                    request);
            throw new ForbiddenException(ACCOUNT_NOT_VERIFIED_MESSAGE);
        }

        boolean profileChanged =
                (update.username() != null && !update.username().equals(user.getUsername()))
                || (update.firstName() != null && !update.firstName().equals(user.getFirstName()))
                || (update.lastName() != null && !update.lastName().equals(user.getLastName()));

        user.setUsername(update.username() == null ? user.getUsername() : update.username());
        user.setFirstName(update.firstName() == null ? user.getFirstName() : update.firstName());
        user.setLastName(update.lastName() == null ? user.getLastName() : update.lastName());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (e.getRootCause() instanceof SQLException sqlEx
                    && SQL_STATE_VIOLATION.equals(sqlEx.getSQLState())) {
                throw new ConflictException("Cannot use this username");
            }
            throw e;
        }

        if (profileChanged) {
            hostEventService.logEvent(
                    hostEventService.recordHost(user, request),
                    EventLogSeverity.INFO,
                    "[PROFILE]: Profile data changed",
                    request);
        }

        return userMapper.toDomain(user);
    }

    @Transactional
    public void deleteUser(UUID userId, Authentication authentication, HttpServletRequest request) {
        RBACRules.requireOwner(UUID.fromString(authentication.getName()), userId);

        JUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        userRepository.delete(user);
        userRepository.flush();

        accountDeletionNotificationService.sendAccountDeletionNotification(user, request);
    }

    @Transactional(readOnly = true)
    public PageResponse<User> listUsers(String search, String order, String role, int page, int size) {
        dataValidator.validateSearchString(search);
        dataValidator.validatePagination(page, size);

        PageRequest pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(
                        "desc".equalsIgnoreCase(order) ?
                                Sort.Direction.DESC :
                                Sort.Direction.ASC,
                        "createdAt", "id"));

        return userMapper.toPageResponse(
                userRepository.findByRoleAndSearch(
                        "superuser".equalsIgnoreCase(role) ? UserRole.SUPERUSER : UserRole.GUEST,
                        (search == null || search.isBlank()) ? null : search.toLowerCase(Locale.ROOT),
                        pageable),
                page,
                size);
    }
}
