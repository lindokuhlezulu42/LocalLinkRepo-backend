package za.co.locallink.enquiry;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import za.co.locallink.account.AccountRole;
import za.co.locallink.account.UserAccount;
import za.co.locallink.provider.ListingStatus;
import za.co.locallink.provider.ProviderListingRepository;

@Service
public class EnquiryService {
    private final EnquiryRepository enquiries;
    private final ProviderListingRepository providers;

    public EnquiryService(EnquiryRepository enquiries, ProviderListingRepository providers) {
        this.enquiries = enquiries;
        this.providers = providers;
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
                .map(EnquiryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnquiryResponse> findAllForAdmin() {
        return enquiries.findAllByOrderByCreatedAtDesc().stream()
                .map(EnquiryResponse::from)
                .toList();
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
        if (enquiries.deleteReadById(enquiryId) == 1) {
            return;
        }
        if (enquiries.existsById(enquiryId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Mark the enquiry as read before deleting it.");
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found.");
    }

    @Transactional
    public void hideForProvider(UUID enquiryId, UserAccount provider) {
        var enquiry = enquiries.findByIdAndProvider_Owner_Id(enquiryId, provider.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        enquiry.setHiddenForProvider(true);
        enquiries.save(enquiry);
    }
}
