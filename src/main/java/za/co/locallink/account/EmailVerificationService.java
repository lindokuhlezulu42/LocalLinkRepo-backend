package za.co.locallink.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailVerificationService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository accounts;
    private final EmailVerificationTokenRepository tokens;
    private final JavaMailSender mailSender;
    private final String frontendOrigin;
    private final String fromAddress;

    public EmailVerificationService(
            UserAccountRepository accounts,
            EmailVerificationTokenRepository tokens,
            JavaMailSender mailSender,
            @Value("${locallink.frontend-origin}") String frontendOrigin,
            @Value("${locallink.mail.from}") String fromAddress) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.mailSender = mailSender;
        this.frontendOrigin = frontendOrigin.replaceAll("/+$", "");
        this.fromAddress = fromAddress;
    }

    public void sendVerification(UserAccount account) {
        sendNewToken(account);
    }

    @Transactional
    public void resend(String email) {
        accounts.findByEmailIgnoreCase(email.trim())
                .filter(account -> !account.isEmailVerified())
                .filter(account -> account.getVerificationSentAt() == null
                        || account.getVerificationSentAt().plus(RESEND_COOLDOWN).isBefore(Instant.now()))
                .ifPresent(this::sendNewToken);
    }

    @Transactional
    public void verify(String rawToken) {
        var token = tokens.findById(hash(rawToken))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "This verification link is invalid or has expired."));
        if (!token.getExpiresAt().isAfter(Instant.now())) {
            tokens.delete(token);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "This verification link is invalid or has expired.");
        }

        var account = token.getAccount();
        account.setEmailVerified(true);
        accounts.save(account);
        tokens.deleteAllByAccount_Id(account.getId());
    }

    private void sendNewToken(UserAccount account) {
        var randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        var rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        tokens.deleteAllByAccount_Id(account.getId());
        tokens.save(new EmailVerificationToken(
                hash(rawToken),
                account,
                Instant.now().plus(TOKEN_LIFETIME)));
        account.setVerificationSentAt(Instant.now());
        accounts.save(account);

        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            var verificationUrl = frontendOrigin + "/verify-email?token=" + rawToken;
            var escapedName = HtmlUtils.htmlEscape(account.getName());
            var escapedUrl = HtmlUtils.htmlEscape(verificationUrl);
            helper.setFrom(fromAddress);
            helper.setTo(account.getEmail());
            helper.setSubject("Verify your LocalLink email address");
            helper.setText("""
                    Hello %s,

                    Confirm that this email address belongs to you by opening this link:
                    %s

                    This link expires in 10 minutes. If you did not create a LocalLink account, you can ignore this message.
                    """.formatted(account.getName(), verificationUrl),
                    """
                    <!doctype html>
                    <html lang="en">
                      <body style="margin:0;padding:32px 16px;background:#f5f6fb;font-family:Arial,Helvetica,sans-serif;color:#20283a;">
                        <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e7e9f1;border-radius:16px;">
                          <tr><td style="padding:36px 32px;">
                            <p style="margin:0 0 12px;color:#5549e6;font-size:12px;font-weight:bold;letter-spacing:1.5px;text-transform:uppercase;">LocalLink</p>
                            <h1 style="margin:0 0 18px;font-size:26px;line-height:1.3;color:#20283a;">Confirm your email</h1>
                            <p style="margin:0 0 14px;font-size:15px;line-height:1.6;">Hello %s,</p>
                            <p style="margin:0 0 24px;color:#687386;font-size:15px;line-height:1.6;">Please confirm that this email address belongs to you by selecting the button below.</p>
                            <p style="margin:0 0 24px;">
                              <a href="%s" style="display:inline-block;padding:14px 22px;border-radius:10px;background:#5549e6;color:#ffffff;font-size:15px;font-weight:bold;text-decoration:none;">Yes, verify my email</a>
                            </p>
                            <p style="margin:0 0 10px;color:#687386;font-size:13px;line-height:1.6;">This link expires in <strong>10 minutes</strong>.</p>
                            <p style="margin:0;color:#8992a2;font-size:12px;line-height:1.6;">If you did not create a LocalLink account, you can ignore this message.</p>
                          </td></tr>
                        </table>
                      </body>
                    </html>
                    """.formatted(escapedName, escapedUrl));
            mailSender.send(message);
        } catch (MailException | jakarta.mail.MessagingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The verification email could not be sent. Please try again later.",
                    exception);
        }
    }

    private static String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
