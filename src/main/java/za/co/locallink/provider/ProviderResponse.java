package za.co.locallink.provider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProviderResponse(
        UUID id,
        UUID ownerId,
        String ownerName,
        String name,
        String category,
        String area,
        String description,
        String price,
        String experience,
        List<String> services,
        String phone,
        String email,
        String status,
        Instant createdAt,
        boolean deletionRequested,
        boolean changesResubmitted,
        String changeRejectionReason,
        String pendingName,
        String pendingCategory,
        String pendingArea,
        String pendingDescription,
        String pendingPrice,
        String pendingExperience,
        List<String> pendingServices,
        String pendingPhone,
        String pendingEmail) {
    public static ProviderResponse from(ProviderListing listing) {
        return new ProviderResponse(
                listing.getId(),
                listing.getOwner().getId(),
                listing.getOwner().getName(),
                listing.getName(),
                listing.getCategory(),
                listing.getArea(),
                listing.getDescription(),
                listing.getPrice(),
                listing.getExperience(),
                listing.getServices(),
                listing.getPhone(),
                listing.getEmail(),
                listing.getStatus().name().toLowerCase(java.util.Locale.ROOT),
                listing.getCreatedAt(),
                listing.isDeletionRequested(),
                listing.isChangesResubmitted(),
                listing.getChangeRejectionReason(),
                listing.getPendingName(),
                listing.getPendingCategory(),
                listing.getPendingArea(),
                listing.getPendingDescription(),
                listing.getPendingPrice(),
                listing.getPendingExperience(),
                listing.getPendingServices(),
                listing.getPendingPhone(),
                listing.getPendingEmail());
    }

    public static ProviderResponse publicFrom(ProviderListing listing) {
        var response = from(listing);
        return new ProviderResponse(
                response.id(),
                response.ownerId(),
                response.ownerName(),
                response.name(),
                response.category(),
                response.area(),
                response.description(),
                response.price(),
                response.experience(),
                response.services(),
                response.phone(),
                response.email(),
                response.status(),
                response.createdAt(),
                response.deletionRequested(),
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null);
    }
}
