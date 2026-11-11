package za.co.locallink.provider;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record ProviderRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank String category,
        @NotBlank @Size(max = 120) String area,
        @NotBlank @Size(max = 1600) String description,
        @Size(max = 100) String price,
        @Size(max = 60) String experience,
        @NotEmpty @Size(max = 12) List<@NotBlank @Size(max = 100) String> services,
        @NotBlank @Size(max = 40) String phone,
        @NotBlank @Email @Size(max = 254) String email) {
}
