package za.co.locallink.review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ReviewDtos {
    public record PublicReview(UUID id, String residentName, int rating, String comment, Instant createdAt, UUID enquiryId) {}
    public record ProviderReviews(double averageRating, long reviewCount, List<PublicReview> reviews) {}
}
