package com.techindna.relay.service.mail;

import com.techindna.relay.entity.email.EmailDetails;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender javaMailSender;
    private final ITemplateEngine templateEngine;
    @Value("${spring.mail.username:}")
    private String sender;


    public void sendMail(EmailDetails details) {
        if (!StringUtils.hasText(sender)) {
            throw new MailSendException("Mail sender is not configured (spring.mail.username)");
        }
        try {
            Context context = new Context(Locale.ENGLISH, details.variables());
            String html = templateEngine.process(details.template().view(), context);

            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(sender);
            helper.setTo(details.recipient());
            helper.setSubject(details.subject());
            helper.setText(html, true);

            javaMailSender.send(mimeMessage);
        } catch (MessagingException | MailException | TemplateProcessingException e) {
            log.error("Failed to send email using template {}", details.template(), e);
            throw new MailSendException("Failed to send email to recipient", e);
        }
    }
}
