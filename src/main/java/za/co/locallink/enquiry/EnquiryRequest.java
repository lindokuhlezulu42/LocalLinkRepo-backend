package za.co.locallink.enquiry;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnquiryRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 40) String phone,
        @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 1200) String message) {
}
