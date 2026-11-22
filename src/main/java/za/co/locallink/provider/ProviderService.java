package za.co.locallink.provider;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import za.co.locallink.account.UserAccount;
import za.co.locallink.enquiry.EnquiryRepository;
import za.co.locallink.appointment.AppointmentSlotRepository;
import za.co.locallink.appointment.BookingRequestRepository;
import za.co.locallink.review.ReviewRepository;

@Service
public class ProviderService {
    private static final Set<String> CATEGORIES = Set.of(
            "plumbing", "electrical", "mechanics", "tutoring",
            "cleaning", "building", "gardening", "beauty");

    private final ProviderListingRepository listings;
    private final EnquiryRepository enquiries;
    private final BookingRequestRepository bookings;
    private final ReviewRepository reviews;
    private final AppointmentSlotRepository slots;

    public ProviderService(
            ProviderListingRepository listings,
            EnquiryRepository enquiries) {
        this(listings, enquiries, null, null, null);
    }

    @Autowired
    public ProviderService(
            ProviderListingRepository listings,
            EnquiryRepository enquiries,
            BookingRequestRepository bookings,
            ReviewRepository reviews,
            AppointmentSlotRepository slots) {
        this.listings = listings;
        this.enquiries = enquiries;
        this.bookings = bookings;
        this.reviews = reviews;
        this.slots = slots;
    }

    @Transactional(readOnly = true)
    public List<ProviderResponse> search(String category, String area, String query, Integer limit) {
        // Format strings in Java to bypass PostgreSQL type inference bugs with CONCAT/LOWER
        String categoryLower = normalize(category);
        String areaPattern = (area != null && !area.trim().isEmpty()) ? "%" + area.trim().toLowerCase(Locale.ROOT) + "%" : null;
        String queryPattern = (query != null && !query.trim().isEmpty()) ? "%" + query.trim().toLowerCase(Locale.ROOT) + "%" : null;

        var list = listings.searchApproved(
                        ListingStatus.APPROVED,
                        categoryLower,
                        areaPattern,
                        queryPattern)
                .stream()
                .map(ProviderResponse::publicFrom)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (reviews != null && !list.isEmpty()) {
            java.util.Map<UUID, Object[]> summaries = new java.util.HashMap<>();
            for (Object[] row : reviews.summarizeByProviderIds(
                    list.stream().map(ProviderResponse::id).toList())) {
                summaries.put((UUID) row[0], row);
            }
            for (int index = 0; index < list.size(); index++) {
                var row = summaries.get(list.get(index).id());
                if (row != null) {
                    list.set(index, list.get(index).withRatings((Double) row[1], (Long) row[2]));
                }
            }
        }
        var result = java.util.List.<ProviderResponse>copyOf(list);
        return limit == null ? result : result.stream().limit(Math.max(1, Math.min(limit, 24))).toList();
    }

    @Transactional(readOnly = true)
    public ProviderResponse getPublic(UUID id) {
        var listing = listings.findByIdAndStatus(id, ListingStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service provider not found."));
        var response = ProviderResponse.publicFrom(listing);
        if (reviews != null) {
            var average = reviews.averageRatingFor(id);
            response = response.withRatings(average, reviews.countByProviderId(id));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<ProviderResponse> findOwned(UserAccount account, String status) {
        ListingStatus filter = parseOptionalStatus(status);
        return listings.findOwned(account.getId(), filter).stream().map(ProviderResponse::from).toList();
    }

    @Transactional
    public ProviderResponse create(UserAccount account, ProviderRequest request) {
        validateCategory(request.category());
        var duplicate = listings.findByOwner_IdOrderByCreatedAtDesc(account.getId()).stream()
                .anyMatch(existing -> existing.getName().equalsIgnoreCase(request.name().trim()));
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a listing with that name.");
        }
        var listing = new ProviderListing(
                account,
                request.name().trim(),
                request.category().trim(),
                request.area().trim(),
                request.description().trim(),
                blankToNull(request.price()),
                blankToNull(request.experience()),
                request.services().stream().map(String::trim).filter(value -> !value.isBlank()).distinct().toList(),
                request.phone().trim(),
                request.email().trim().toLowerCase(Locale.ROOT));
        if (listing.getServices().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one service offered.");
        }
        return ProviderResponse.from(listings.save(listing));
    }

    @Transactional
    public ProviderResponse update(UserAccount account, UUID id, ProviderRequest request) {
        validateCategory(request.category());
        var listing = listings.findById(id)
                .filter(existing -> existing.getOwner().getId().equals(account.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
        if (listing.isChangesResubmitted()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This listing already has changes awaiting administrator review.");
        }
        var duplicate = listings.findByOwner_IdOrderByCreatedAtDesc(account.getId()).stream()
                .anyMatch(existing -> !existing.getId().equals(id)
                        && existing.getName().equalsIgnoreCase(request.name().trim()));
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a listing with that name.");
        }
        var services = request.services().stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (services.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one service offered.");
        }
        var name = request.name().trim();
        var category = request.category().trim();
        var area = request.area().trim();
        var description = request.description().trim();
        var price = blankToNull(request.price());
        var experience = blankToNull(request.experience());
        var phone = request.phone().trim();
        var email = request.email().trim().toLowerCase(Locale.ROOT);
        if (listing.getStatus() == ListingStatus.PENDING && !listing.isChangesResubmitted()) {
            listing.updateDetails(name, category, area, description, price, experience, services, phone, email);
        } else {
            listing.stageChanges(name, category, area, description, price, experience, services, phone, email);
        }
        return ProviderResponse.from(listings.save(listing));
    }

    @Transactional
    public void deleteOwned(UserAccount account, UUID id) {
        var listing = listings.findById(id)
                .filter(existing -> existing.getOwner().getId().equals(account.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
        if (bookings != null) bookings.deleteAllByProviderId(id);
        if (reviews != null) reviews.deleteAllByProviderId(id);
        if (slots != null) slots.deleteAllByProviderId(id);
        enquiries.deleteAllByProviderId(id);
        listings.delete(listing);
    }

    @Transactional(readOnly = true)
    public List<ProviderResponse> findPending() {
        return listings.findByStatusOrderByCreatedAtDesc(ListingStatus.PENDING)
                .stream().map(ProviderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ProviderResponse> findAllForAdmin() {
        return listings.findAllByOrderByCreatedAtDesc().stream()
                .map(ProviderResponse::from)
                .toList();
    }

    @Transactional
    public ProviderResponse updateStatus(UUID id, String status) {
        ListingStatus newStatus;
        try {
            newStatus = ListingStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid listing status.");
        }
        if (newStatus != ListingStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Administrators can only approve listings. Providers request their own changes.");
        }
        var listing = listings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
        if (listing.isChangesResubmitted()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Review the proposed changes using the changes approval action.");
        }
        listing.setStatus(newStatus);
        return ProviderResponse.from(listings.save(listing));
    }

    @Transactional
    public ProviderResponse approveResubmittedChanges(UUID id) {
        var listing = listings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
        if (!listing.isChangesResubmitted()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This listing has no resubmitted changes awaiting approval.");
        }
        listing.approveStagedChanges();
        listing.setStatus(ListingStatus.APPROVED);
        return ProviderResponse.from(listings.save(listing));
    }

    @Transactional
    public ProviderResponse rejectResubmittedChanges(UUID id, String reason) {
        var listing = listings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
        if (!listing.isChangesResubmitted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This listing has no proposed changes awaiting review.");
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a reason for rejecting the proposed changes.");
        }
        listing.rejectStagedChanges(reason.trim());
        return ProviderResponse.from(listings.save(listing));
    }

    private static void validateCategory(String category) {
        if (category == null || !CATEGORIES.contains(category.trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid service category.");
        }
    }

    private static ListingStatus parseOptionalStatus(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("all")) return null;
        try {
            return ListingStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid listing status.");
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}