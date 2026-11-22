package za.co.locallink.review;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import za.co.locallink.account.AccountRole;
import za.co.locallink.account.UserAccount;
import za.co.locallink.enquiry.EnquiryRepository;
import za.co.locallink.provider.ListingStatus;
import za.co.locallink.provider.ProviderListingRepository;

@Service
public class ReviewService {
    private final EnquiryRepository enquiries;
    private final ReviewRepository reviews;
    private final ProviderListingRepository providers;

    public ReviewService(EnquiryRepository enquiries, ReviewRepository reviews, ProviderListingRepository providers) {
        this.enquiries = enquiries;
        this.reviews = reviews;
        this.providers = providers;
    }

    @Transactional
    public ReviewDtos.PublicReview create(UUID enquiryId, ReviewRequest request, UserAccount resident) {
        if (resident == null || resident.getRole() != AccountRole.RESIDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only residents can submit reviews.");
        }
        var enquiry = enquiries.findById(enquiryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        if (enquiry.getResident() == null
                || !enquiry.getResident().getId().equals(resident.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The enquiry is not attached to this resident.");
        }
        if (!"completed".equals(enquiry.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The enquiry must be completed before review.");
        }
        if (reviews.existsByEnquiryId(enquiryId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A review has already been submitted for this enquiry.");
        }
        var provider = enquiry.getProvider();
        if (provider.getStatus() != ListingStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reviews are available only for approved providers.");
        }
        var comment = request.comment() == null ? null : request.comment().trim();
        var saved = reviews.save(new Review(enquiry, request.rating(), comment));
        return new ReviewDtos.PublicReview(saved.getId(), resident.getName(), saved.getRating(),
                saved.getComment(), saved.getCreatedAt(), enquiryId);
    }

    @Transactional(readOnly = true)
    public ReviewDtos.ProviderReviews findPublic(UUID providerId) {
        var provider = providers.findByIdAndStatus(providerId, ListingStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approved provider not found."));
        var list = reviews.findByProviderIdOrderByCreatedAtDesc(providerId).stream()
                .map(review -> new ReviewDtos.PublicReview(
                        review.getId(),
                        review.getResident().getName(),
                        review.getRating(),
                        review.getComment(),
                        review.getCreatedAt(),
                        review.getEnquiry().getId()))
                .toList();
        double average = list.isEmpty() ? 0.0 : list.stream().mapToInt(r -> r.rating()).average().orElse(0.0);
        return new ReviewDtos.ProviderReviews(average, list.size(), list);
    }
}
