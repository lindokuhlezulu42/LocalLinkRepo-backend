package za.co.locallink.account;

import java.util.UUID;

public record AccountResponse(UUID id, String name, String email, String role) {
    public static AccountResponse from(UserAccount account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getEmail(),
                account.getRole().name().toLowerCase(java.util.Locale.ROOT));
    }
}
