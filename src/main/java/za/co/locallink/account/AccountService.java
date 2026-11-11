package za.co.locallink.account;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService implements org.springframework.security.core.userdetails.UserDetailsService {
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerification;

    public AccountService(
            UserAccountRepository accounts,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerification) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.emailVerification = emailVerification;
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccount loadUserByUsername(String email) {
        return accounts.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(
                        "Account not found"));
    }

    @Transactional
    public UserAccount register(RegisterRequest request) {
        var email = request.email().trim().toLowerCase(Locale.ROOT);
        if (accounts.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists.");
        }

        AccountRole role = switch (request.role().trim().toLowerCase(Locale.ROOT)) {
            case "resident" -> AccountRole.RESIDENT;
            case "provider" -> AccountRole.PROVIDER;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid account type.");
        };
        var account = new UserAccount(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                role);
        account.setEmailVerified(false);
        account = accounts.save(account);
        emailVerification.sendVerification(account);
        return account;
    }
}
