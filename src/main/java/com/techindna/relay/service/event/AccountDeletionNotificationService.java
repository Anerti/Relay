package com.techindna.relay.service.event;

import com.techindna.relay.config.ClientDataConfig;
import com.techindna.relay.entity.email.EmailDetails;
import com.techindna.relay.entity.email.EmailTemplate;
import com.techindna.relay.exception.http.InternalServerErrorException;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.service.mail.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountDeletionNotificationService {

    private final EmailService emailService;
    private final ClientDataConfig clientDataConfig;

    public void sendAccountDeletionNotification(JUser user, HttpServletRequest request) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("firstName", user.getFirstName());
        variables.put("ipAddress", clientDataConfig.ipAddress(request));
        variables.put("userAgent", clientDataConfig.userAgent(request));
        variables.put("time", DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)));

        try {
            emailService.sendMail(new EmailDetails(
                    EmailTemplate.ACCOUNT_DELETION_NOTIFICATION,
                    user.getEmail(),
                    "Your account was deleted",
                    variables));
        } catch (MailException e) {
            throw new InternalServerErrorException("Failed to send account-deletion notification email", e);
        }
    }
}
