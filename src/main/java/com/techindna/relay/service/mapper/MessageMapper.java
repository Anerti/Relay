package com.techindna.relay.service.mapper;

import com.techindna.relay.dto.Meta;
import com.techindna.relay.dto.PageResponse;
import com.techindna.relay.entity.Message;
import com.techindna.relay.repository.model.JMessage;
import java.util.List;
import org.springframework.data.domain.Page;
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

    public PageResponse<Message> toPageResponse(Page<JMessage> messages, int page, int size) {
        List<Message> data = messages.getContent().stream().map(this::toDomain).toList();

        return new PageResponse<>(
                data.isEmpty() ? null : data,
                new Meta(page, size, messages.getTotalElements())
        );
    }
}
