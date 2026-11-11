package za.co.locallink.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(
            UserAccountRepository accounts,
            PasswordEncoder passwordEncoder,
            @Value("${locallink.admin.email}") String email,
            @Value("${locallink.admin.password}") String password) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.isBlank() || password.length() < 12) {
            throw new IllegalStateException(
                    "Configure both LOCALLINK_ADMIN_EMAIL and a LOCALLINK_ADMIN_PASSWORD of at least 12 characters.");
        }
        if (!accounts.existsByEmailIgnoreCase(email)) {
            accounts.save(new UserAccount(
                    "LocalLink Admin",
                    email.trim(),
                    passwordEncoder.encode(password),
                    AccountRole.ADMIN));
        }
    }
}
