package com.techindna.relay.service.mapper;

import com.techindna.relay.entity.Message;
import com.techindna.relay.repository.model.JMessage;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {

    public Message toDomain(JMessage message) {
        return new Message(
                message.getId(),
                message.getSenderId(),
                message.getReceiver() == null ? null : message.getReceiver().getId(),
                message.getReplyTo(),
                message.getIssuer(),
                message.getContent(),
                message.getCreatedAt(),
                message.getUpdatedAt());
    }
}
