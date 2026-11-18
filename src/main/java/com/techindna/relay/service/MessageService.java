package com.techindna.relay.service;

import com.techindna.relay.dto.SendMessageInput;
import com.techindna.relay.entity.Message;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.exception.http.NotFoundException;
import com.techindna.relay.repository.MessageRepository;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JMessage;
import com.techindna.relay.service.event.HostEventService;
import com.techindna.relay.service.mapper.MessageMapper;
import com.techindna.relay.validator.MessageValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final MessageMapper messageMapper;
    private final HostEventService hostEventService;

    @Transactional
    public Message sendMessage(
            UUID userId, SendMessageInput input, Authentication authentication, HttpServletRequest request) {
        messageValidator.validateSend(input);

        UUID senderId = UUID.fromString(authentication.getName());
        JMessage message = JMessage.builder()
                .senderId(senderId)
                .receiver(userRepository.findByIdAndVerifiedTrue(userId)
                        .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND)))
                .content(input.content())
                .build();

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
                hostEventService.recordHost(senderId, request),
                EventLogSeverity.INFO,
                "[MESSAGE]: Message sent to " + userId,
                request);

        return messageMapper.toDomain(message);
    }
}
