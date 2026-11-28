package com.techindna.relay.service;

import com.techindna.relay.dto.PageResponse;
import com.techindna.relay.dto.SendMessageInput;
import com.techindna.relay.entity.Message;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.exception.http.ForbiddenException;
import com.techindna.relay.exception.http.NotFoundException;
import com.techindna.relay.repository.MessageRepository;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JMessage;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.service.event.HostEventService;
import com.techindna.relay.service.mapper.MessageMapper;
import com.techindna.relay.validator.DataValidator;
import com.techindna.relay.validator.MessageValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.time.Instant;
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
public class MessageService {

    private static final String SQL_STATE_FK_VIOLATION = "23503";
    private static final String USER_NOT_FOUND = "User not found";

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageValidator messageValidator;
    private final DataValidator dataValidator;
    private final MessageMapper messageMapper;
    private final HostEventService hostEventService;

    @Transactional(noRollbackFor = ForbiddenException.class)
    public PageResponse<Message> listMessages(
            UUID userId, String order, int page, int size, Authentication authentication, HttpServletRequest request) {
        dataValidator.validatePagination(page, size);

        JUser caller = userRepository.findById(UUID.fromString(authentication.getName()))
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));

        JHost host = hostEventService.recordHost(caller, request);
        assertAllowed(caller, host, "View", request);

        PageResponse<Message> messages = messageMapper.toPageResponse(
                messageRepository.findConversation(
                        caller.getId(),
                        userId,
                        PageRequest.of(
                                page - 1,
                                size,
                                Sort.by(
                                        "desc".equalsIgnoreCase(order) ?
                                                Sort.Direction.DESC :
                                                Sort.Direction.ASC,
                                        "createdAt", "id"))),
                page,
                size);

        hostEventService.logEvent(
                host, EventLogSeverity.INFO, "[MESSAGE]: Conversation read with " + userId, request);

        return messages;
    }

    @Transactional(noRollbackFor = ForbiddenException.class)
    public Message sendMessage(
            UUID userId, SendMessageInput input, Authentication authentication, HttpServletRequest request) {
        messageValidator.validateSend(input);

        JUser sender = userRepository.findById(UUID.fromString(authentication.getName()))
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));

        JMessage message = JMessage.builder()
                .senderId(sender.getId())
                .receiver(userRepository.findByIdAndVerifiedTrue(userId)
                        .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND)))
                .content(input.content())
                .build();

        JHost host = hostEventService.recordHost(sender, request);
        assertAllowed(sender, host, "Send", request);

        try {
            messageRepository.saveAndFlush(message);
        } catch (DataIntegrityViolationException e) {
            if (e.getRootCause() instanceof SQLException sqlEx
                    && SQL_STATE_FK_VIOLATION.equals(sqlEx.getSQLState())) {
                throw new NotFoundException(USER_NOT_FOUND);
            }
            throw e;
        }

        hostEventService.logEvent(
                host, EventLogSeverity.INFO, "[MESSAGE]: Message sent to " + userId, request);

        return messageMapper.toDomain(message);
    }

    @Transactional(noRollbackFor = ForbiddenException.class)
    public void deleteMessage(UUID messageId, Authentication authentication, HttpServletRequest request) {
        JUser caller = userRepository.findById(UUID.fromString(authentication.getName()))
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));

        JMessage message = messageRepository.findByIdAndSenderIdAndDeletedAtIsNull(messageId, caller.getId())
                .orElseThrow(() -> new NotFoundException("Message not found"));

        JHost host = hostEventService.recordHost(caller, request);
        assertAllowed(caller, host, "Delete", request);

        message.setDeletedAt(Instant.now());
        messageRepository.saveAndFlush(message);

        hostEventService.logEvent(
                host, EventLogSeverity.INFO, "[MESSAGE]: Message " + messageId + " deleted", request);
    }

    private void assertAllowed(JUser user, JHost host, String action, HttpServletRequest request) {
        hostEventService.rejectLockedAccount(user, host,
                "[MESSAGE]: " + action + " failed due to locked account",
                "This account is locked and cannot " + action.toLowerCase(Locale.ROOT) + " messages.",
                request);
        hostEventService.rejectBannedHost(host, "[MESSAGE]: " + action + " failed due to banned host", request);
    }
}
