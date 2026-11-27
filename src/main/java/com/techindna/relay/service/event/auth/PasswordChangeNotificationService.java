package com.techindna.relay.service.event.auth;

import com.techindna.relay.entity.email.EmailDetails;
import com.techindna.relay.entity.email.EmailTemplate;
import com.techindna.relay.exception.http.InternalServerErrorException;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.service.mail.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordChangeNotificationService {

    private final EmailService emailService;
    private final ClientDetailsService clientDetailsService;

    public void sendPasswordChangeNotification(JUser user, HttpServletRequest request) {
        Map<String, Object> variables = clientDetailsService.clientVariables(request);
        variables.put("firstName", user.getFirstName());
        variables.put("time", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        try {
            emailService.sendMail(new EmailDetails(
                    EmailTemplate.PASSWORD_CHANGE_NOTIFICATION,
                    user.getEmail(),
                    "Your password was changed",
                    variables));
        } catch (MailException e) {
            throw new InternalServerErrorException("Failed to send password-change notification email", e);
        }
    }
}
