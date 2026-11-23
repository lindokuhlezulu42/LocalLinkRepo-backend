package za.co.locallink.enquiry;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import za.co.locallink.account.AccountRole;
import za.co.locallink.account.UserAccount;
import za.co.locallink.provider.ListingStatus;
import za.co.locallink.provider.ProviderListingRepository;
import za.co.locallink.review.ReviewRepository;

@Service
public class EnquiryService {
    private final EnquiryRepository enquiries;
    private final ProviderListingRepository providers;
    private final ReviewRepository reviews;

    public EnquiryService(EnquiryRepository enquiries, ProviderListingRepository providers) {
        this(enquiries, providers, null);
    }

    @Autowired
    public EnquiryService(EnquiryRepository enquiries, ProviderListingRepository providers, ReviewRepository reviews) {
        this.enquiries = enquiries;
        this.providers = providers;
        this.reviews = reviews;
    }

    @Transactional
    public EnquiryResponse create(UUID providerId, UserAccount resident, EnquiryRequest request) {
        var provider = providers.findByIdAndStatus(providerId, ListingStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service provider not found."));
        var email = request.email() == null || request.email().isBlank()
                ? null
                : request.email().trim().toLowerCase(java.util.Locale.ROOT);
        var enquiry = new Enquiry(
                provider,
                resident != null && resident.getRole() == AccountRole.RESIDENT ? resident : null,
                request.name().trim(),
                request.phone().trim(),
                email,
                request.message().trim());
        return EnquiryResponse.from(enquiries.save(enquiry));
    }

    @Transactional(readOnly = true)
    public List<EnquiryResponse> findMine(UserAccount account) {
        return enquiries.findVisibleToAccount(account.getId()).stream()
                .map(enquiry -> EnquiryResponse.from(
                        enquiry,
                        enquiry.getResident() != null
                                && enquiry.getResident().getId().equals(account.getId())
                                && reviews != null
                                && reviews.existsByEnquiryId(enquiry.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnquiryResponse> findAllForAdmin() {
        return enquiries.findAllByHiddenForAdminFalseOrderByCreatedAtDesc().stream()
                .map(EnquiryResponse::from)
                .toList();
    }

    @Transactional
    public void hideForAdmin(UUID enquiryId) {
        var enquiry = enquiries.findById(enquiryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        enquiry.setHiddenForAdmin(true);
        enquiries.save(enquiry);
    }

    @Transactional
    public EnquiryResponse setReadByAdmin(UUID enquiryId, boolean read) {
        var enquiry = enquiries.findById(enquiryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        enquiry.setReadByAdmin(read);
        return EnquiryResponse.from(enquiries.save(enquiry));
    }

    @Transactional
    public void deleteReadByAdmin(UUID enquiryId) {
        var enquiry = enquiries.findById(enquiryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Enquiry not found."));
        if (!enquiry.isReadByAdmin()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Mark the enquiry as read before deleting it.");
        }
        if (reviews != null) reviews.deleteAllByEnquiryId(enquiryId);
        if (enquiries.deleteReadById(enquiryId) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The enquiry could not be deleted.");
        }
    }

    @Transactional
    public void hideForProvider(UUID enquiryId, UserAccount provider) {
        var enquiry = enquiries.findByIdAndProvider_Owner_Id(enquiryId, provider.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        enquiry.setHiddenForProvider(true);
        enquiries.save(enquiry);
    }

    @Transactional
    public void hideForResident(UUID enquiryId, UserAccount resident) {
        var enquiry = enquiries.findById(enquiryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        if (enquiry.getResident() == null
                || !enquiry.getResident().getId().equals(resident.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The enquiry is not attached to this resident.");
        }
        enquiry.setHiddenForResident(true);
        enquiries.save(enquiry);
    }

    @Transactional
    public EnquiryResponse changeStatus(UUID enquiryId, String status, UserAccount provider) {
        var enquiry = enquiries.findByIdAndProvider_Owner_Id(enquiryId, provider.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        String current = enquiry.getStatus();
        boolean allowed = ("new".equals(current) && "accepted".equals(status))
                || ("accepted".equals(current) && "in_progress".equals(status))
                || ("in_progress".equals(current) && "completed".equals(status));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid enquiry status transition.");
        }
        enquiry.setStatus(status);
        return EnquiryResponse.from(enquiries.save(enquiry));
    }
}
