package za.co.locallink.account;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    void deleteAllByAccount_Id(UUID accountId);

    boolean existsByAccount_IdAndCreatedAtAfter(UUID accountId, Instant since);
}
