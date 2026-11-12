package com.techindna.relay.service.event.auth;

import com.techindna.relay.entity.email.EmailDetails;
import com.techindna.relay.entity.email.EmailTemplate;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.service.enums.VerificationFlow;
import com.techindna.relay.service.mail.EmailService;
import com.techindna.relay.service.redis.VerificationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationEmailService {

    private final VerificationTokenService verificationTokenService;
    private final EmailService emailService;
    private final ClientDetailsService clientDetailsService;

    @Value("${app.base-url}")
    private String baseUrl;

    public void sendRegistrationVerification(JUser user, HttpServletRequest request) {
        Map<String, Object> variables = clientDetailsService.clientVariables(request);
        variables.put("firstName", user.getFirstName());
        variables.put("lastName", user.getLastName());
        variables.put("username", user.getUsername());
        variables.put("email", user.getEmail());
        variables.put("time", DateTimeFormatter.ISO_INSTANT.format(user.getCreatedAt()));

        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.REGISTER);
        variables.put("verificationUrl", baseUrl.replaceAll("/+$", "") + "/auth/verify/" + token);

        try {
            emailService.sendMail(new EmailDetails(
                    EmailTemplate.REGISTRATION_VERIFICATION,
                    user.getEmail(),
                    "Verify your account",
                    variables));
        } catch (RuntimeException e) {
            verificationTokenService.delete(token);
            throw e;
        }
    }
}
