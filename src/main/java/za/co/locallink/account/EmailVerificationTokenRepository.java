package za.co.locallink.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, String> {
    void deleteAllByAccount_Id(UUID accountId);
}
