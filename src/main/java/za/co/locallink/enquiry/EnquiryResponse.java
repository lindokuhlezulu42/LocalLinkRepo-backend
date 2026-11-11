package za.co.locallink.enquiry;

import java.time.Instant;
import java.util.UUID;

public record EnquiryResponse(
        UUID id,
        UUID providerId,
        String providerName,
        UUID userId,
        String name,
        String phone,
        String email,
        String message,
        String status,
        Instant createdAt,
        boolean readByAdmin) {
    public static EnquiryResponse from(Enquiry enquiry) {
        return new EnquiryResponse(
                enquiry.getId(),
                enquiry.getProvider().getId(),
                enquiry.getProvider().getName(),
                enquiry.getResident() == null ? null : enquiry.getResident().getId(),
                enquiry.getName(),
                enquiry.getPhone(),
                enquiry.getEmail(),
                enquiry.getMessage(),
                enquiry.getStatus(),
                enquiry.getCreatedAt(),
                enquiry.isReadByAdmin());
    }
}
