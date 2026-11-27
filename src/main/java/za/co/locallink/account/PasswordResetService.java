package za.co.locallink.account;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    private static final Duration REQUEST_COOLDOWN = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVALID_LINK = "This reset link is invalid or has expired.";

    private final UserAccountRepository accounts;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String frontendOrigin;
    private final String fromAddress;

    public PasswordResetService(
            UserAccountRepository accounts,
            PasswordResetTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            @Value("${locallink.frontend-origin}") String frontendOrigin,
            @Value("${locallink.mail.from}") String fromAddress) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.frontendOrigin = frontendOrigin.split(",")[0].trim().replaceAll("/+$", "");
        this.fromAddress = fromAddress;
    }

    /**
     * Sends a reset link when the address belongs to a non-admin account. It always
     * completes the same way so the response does not reveal which emails exist.
     * The admin password is managed through LOCALLINK_ADMIN_PASSWORD instead.
     */
    @Transactional
    public void requestReset(String email) {
        var now = Instant.now();
        accounts.findByEmailIgnoreCase(email.trim())
                .filter(account -> account.getRole() != AccountRole.ADMIN)
                .filter(account -> !tokens.existsByAccount_IdAndCreatedAtAfter(
                        account.getId(), now.minus(REQUEST_COOLDOWN)))
                .ifPresent(account -> sendResetLink(account, now));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        var token = tokens.findById(EmailVerificationService.hash(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_LINK));
        var account = token.getAccount();
        if (!token.getExpiresAt().isAfter(Instant.now()) || account.getRole() == AccountRole.ADMIN) {
            tokens.delete(token);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_LINK);
        }

        account.changePassword(passwordEncoder.encode(newPassword));
        // Opening the emailed link proves the person owns the address.
        account.setEmailVerified(true);
        accounts.save(account);
        tokens.deleteAllByAccount_Id(account.getId());
    }

    private void sendResetLink(UserAccount account, Instant now) {
        var randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        var rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        tokens.deleteAllByAccount_Id(account.getId());
        tokens.save(new PasswordResetToken(
                EmailVerificationService.hash(rawToken), account, now, now.plus(TOKEN_LIFETIME)));

        var resetUrl = frontendOrigin + "/reset-password?token=" + rawToken;
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(account.getEmail());
            helper.setSubject("Reset your LocalLink password");
            helper.setText("""
                    Hello %s,

                    We received a request to reset your LocalLink password. Choose a new password here:
                    %s

                    This link expires in 30 minutes. If you did not ask to reset your password, you can ignore this message. Your password will not change.
                    """.formatted(account.getName(), resetUrl),
                    """
                    <!doctype html>
                    <html lang="en">
                      <body style="margin:0;padding:32px 16px;background:#f5f6fb;font-family:Arial,Helvetica,sans-serif;color:#20283a;">
                        <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e7e9f1;border-radius:16px;">
                          <tr><td style="padding:36px 32px;">
                            <p style="margin:0 0 12px;color:#5549e6;font-size:12px;font-weight:bold;letter-spacing:1.5px;text-transform:uppercase;">LocalLink</p>
                            <h1 style="margin:0 0 18px;font-size:26px;line-height:1.3;color:#20283a;">Reset your password</h1>
                            <p style="margin:0 0 14px;font-size:15px;line-height:1.6;">Hello %s,</p>
                            <p style="margin:0 0 24px;color:#687386;font-size:15px;line-height:1.6;">We received a request to reset your LocalLink password. Select the button below to choose a new one.</p>
                            <p style="margin:0 0 24px;">
                              <a href="%s" style="display:inline-block;padding:14px 22px;border-radius:10px;background:#5549e6;color:#ffffff;font-size:15px;font-weight:bold;text-decoration:none;">Choose a new password</a>
                            </p>
                            <p style="margin:0 0 10px;color:#687386;font-size:13px;line-height:1.6;">This link expires in <strong>30 minutes</strong> and can be used once.</p>
                            <p style="margin:0;color:#8992a2;font-size:12px;line-height:1.6;">If you did not ask to reset your password, you can ignore this message. Your password will not change.</p>
                          </td></tr>
                        </table>
                      </body>
                    </html>
                    """.formatted(HtmlUtils.htmlEscape(account.getName()), HtmlUtils.htmlEscape(resetUrl)));
            mailSender.send(message);
        } catch (MailException | jakarta.mail.MessagingException exception) {
            // Failing loudly here would reveal that the address has an account.
            log.warn("Password reset email could not be sent to account {}", account.getId(), exception);
        }
    }
}
