package za.co.locallink.enquiry;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import za.co.locallink.account.UserAccount;
import za.co.locallink.provider.ProviderListing;
import za.co.locallink.provider.ProviderListingRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnquiryServiceTests {
    @Mock
    private EnquiryRepository enquiries;

    @Mock
    private ProviderListingRepository providers;

    @Test
    void adminCanDeleteReadEnquiry() {
        var enquiryId = UUID.randomUUID();
        var enquiry = new Enquiry(mock(ProviderListing.class), mock(UserAccount.class),
                "Resident", "082 123 4567", null, "Leaking tap.");
        enquiry.setReadByAdmin(true);
        when(enquiries.findById(enquiryId)).thenReturn(Optional.of(enquiry));
        when(enquiries.deleteReadById(enquiryId)).thenReturn(1);

        new EnquiryService(enquiries, providers).deleteReadByAdmin(enquiryId);

        verify(enquiries).deleteReadById(enquiryId);
    }

    @Test
    void adminCannotDeleteUnreadEnquiry() {
        var enquiryId = UUID.randomUUID();
        var enquiry = new Enquiry(mock(ProviderListing.class), mock(UserAccount.class),
                "Resident", "082 123 4567", null, "Leaking tap.");
        enquiry.setReadByAdmin(false);
        when(enquiries.findById(enquiryId)).thenReturn(Optional.of(enquiry));

        var error = assertThrows(
                ResponseStatusException.class,
                () -> new EnquiryService(enquiries, providers).deleteReadByAdmin(enquiryId));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void deletingMissingEnquiryReturnsNotFound() {
        var enquiryId = UUID.randomUUID();
        when(enquiries.findById(enquiryId)).thenReturn(Optional.empty());

        var error = assertThrows(
                ResponseStatusException.class,
                () -> new EnquiryService(enquiries, providers).deleteReadByAdmin(enquiryId));

        assertEquals(404, error.getStatusCode().value());
    }
}
