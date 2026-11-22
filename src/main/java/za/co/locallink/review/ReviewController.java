package za.co.locallink.review;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import za.co.locallink.account.UserAccount;

@RestController
public class ReviewController {
    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) { this.reviews = reviews; }

    @PostMapping("/api/enquiries/{enquiryId}/review")
    public ReviewDtos.PublicReview create(
            @PathVariable UUID enquiryId,
            @AuthenticationPrincipal UserAccount resident,
            @Valid @RequestBody ReviewRequest request) {
        return reviews.create(enquiryId, request, resident);
    }

    @GetMapping("/api/providers/{providerId}/reviews")
    public ReviewDtos.ProviderReviews publicReviews(@PathVariable UUID providerId) {
        return reviews.findPublic(providerId);
    }
}
