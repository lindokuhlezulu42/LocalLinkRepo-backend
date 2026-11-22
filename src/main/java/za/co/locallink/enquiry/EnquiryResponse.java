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
        boolean readByAdmin,
        boolean reviewed) {
    public static EnquiryResponse from(Enquiry enquiry) {
        return from(enquiry, false);
    }

    public EnquiryResponse(
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
        this(id, providerId, providerName, userId, name, phone, email, message,
                status, createdAt, readByAdmin, false);
    }

    public static EnquiryResponse from(Enquiry enquiry, boolean reviewed) {
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
                enquiry.isReadByAdmin(),
                reviewed);
    }
}
