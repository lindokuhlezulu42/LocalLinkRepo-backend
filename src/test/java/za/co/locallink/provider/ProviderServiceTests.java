package za.co.locallink.provider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import za.co.locallink.account.UserAccount;
import za.co.locallink.enquiry.EnquiryRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTests {
    @Mock
    private ProviderListingRepository listings;

    @Mock
    private EnquiryRepository enquiries;

    @Test
    void providerChangesAreSubmittedWithoutReplacingPublishedDetails() {
        var account = mock(UserAccount.class);
        var ownerId = UUID.randomUUID();
        when(account.getId()).thenReturn(ownerId);
        when(account.getName()).thenReturn("Provider");
        var listing = new ProviderListing(
                account,
                "Example Plumbing",
                "Plumbing",
                "Midrand",
                "Pipe repairs",
                null,
                null,
                List.of("Pipe repairs"),
                "0000000000",
                "provider@example.com");
        listing.onCreate();
        listing.setStatus(ListingStatus.APPROVED);
        var listingId = listing.getId();

        when(listings.findById(listingId)).thenReturn(Optional.of(listing));
        when(listings.findByOwner_IdOrderByCreatedAtDesc(ownerId)).thenReturn(List.of(listing));
        when(listings.save(listing)).thenReturn(listing);

        var response = new ProviderService(listings, enquiries).update(
                account,
                listingId,
                new ProviderRequest(
                        "Example Plumbing",
                        "Plumbing",
                        "Midrand",
                        "Updated pipe repairs",
                        null,
                        null,
                        List.of("Pipe repairs"),
                        "0000000000",
                        "provider@example.com"));

        assertEquals("approved", response.status());
        assertTrue(response.changesResubmitted());
        assertEquals("Example Plumbing", response.name());
        assertEquals("Updated pipe repairs", response.pendingDescription());
    }

    @Test
    void administratorCanApproveResubmittedChanges() {
        var account = mock(UserAccount.class);
        when(account.getId()).thenReturn(UUID.randomUUID());
        when(account.getName()).thenReturn("Provider");
        var listing = new ProviderListing(
                account,
                "Example Plumbing",
                "Plumbing",
                "Midrand",
                "Pipe repairs",
                null,
                null,
                List.of("Pipe repairs"),
                "0000000000",
                "provider@example.com");
        listing.onCreate();
        listing.setStatus(ListingStatus.APPROVED);
        listing.stageChanges(
                "Example Plumbing", "Plumbing", "Midrand", "Updated pipe repairs",
                null, null, List.of("Pipe repairs"), "0000000000", "provider@example.com");
        var listingId = listing.getId();

        when(listings.findById(listingId)).thenReturn(Optional.of(listing));
        when(listings.save(listing)).thenReturn(listing);

        var response = new ProviderService(listings, enquiries).approveResubmittedChanges(listingId);

        assertEquals("approved", response.status());
        assertFalse(response.changesResubmitted());
        assertEquals("Updated pipe repairs", response.description());
        assertNull(response.pendingDescription());
    }

    @Test
    void administratorRejectsProposedChangesWithReasonAndKeepsPublishedVersion() {
        var account = mock(UserAccount.class);
        when(account.getName()).thenReturn("Provider");
        var listing = new ProviderListing(
                account, "Example Plumbing", "Plumbing", "Midrand", "Pipe repairs",
                null, null, List.of("Pipe repairs"), "0000000000", "provider@example.com");
        listing.onCreate();
        listing.setStatus(ListingStatus.APPROVED);
        listing.stageChanges(
                "Updated Plumbing", "Plumbing", "Midrand", "Updated description",
                null, null, List.of("Pipe repairs"), "0000000000", "provider@example.com");
        when(listings.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(listings.save(listing)).thenReturn(listing);

        var response = new ProviderService(listings, enquiries)
                .rejectResubmittedChanges(listing.getId(), "Please clarify the service area.");

        assertEquals("approved", response.status());
        assertFalse(response.changesResubmitted());
        assertEquals("Example Plumbing", response.name());
        assertEquals("Please clarify the service area.", response.changeRejectionReason());
    }

    @Test
    void providerCanEditPublishedListingDirectly() {
        var account = mock(UserAccount.class);
        var ownerId = UUID.randomUUID();
        when(account.getId()).thenReturn(ownerId);
        when(account.getName()).thenReturn("Provider");
        var listing = new ProviderListing(
                account, "Example Plumbing", "Plumbing", "Midrand", "Pipe repairs",
                null, null, List.of("Pipe repairs"), "0000000000", "provider@example.com");
        listing.onCreate();
        listing.setStatus(ListingStatus.APPROVED);
        when(listings.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(listings.findByOwner_IdOrderByCreatedAtDesc(ownerId)).thenReturn(List.of(listing));
        when(listings.save(listing)).thenReturn(listing);

        var response = new ProviderService(listings, enquiries).update(
                account,
                listing.getId(),
                new ProviderRequest(
                        "Example Plumbing", "Plumbing", "Midrand", "Updated pipe repairs",
                        null, null, List.of("Pipe repairs"), "0000000000", "provider@example.com"));

        assertEquals("approved", response.status());
        assertTrue(response.changesResubmitted());
        assertEquals("Example Plumbing", response.name());
        assertEquals("Updated pipe repairs", response.pendingDescription());
    }

    @Test
    void providerCannotSubmitAnotherEditWhileChangesAreUnderReview() {
        var account = mock(UserAccount.class);
        var ownerId = UUID.randomUUID();
        when(account.getId()).thenReturn(ownerId);
        var listing = new ProviderListing(
                account, "Example Plumbing", "Plumbing", "Midrand", "Pipe repairs",
                null, null, List.of("Pipe repairs"), "0000000000", "provider@example.com");
        listing.onCreate();
        listing.setStatus(ListingStatus.APPROVED);
        listing.stageChanges(
                "Example Plumbing", "Plumbing", "Midrand", "Submitted pipe repairs",
                null, null, List.of("Pipe repairs"), "0000000000", "provider@example.com");
        when(listings.findById(listing.getId())).thenReturn(Optional.of(listing));

        var error = assertThrows(
                ResponseStatusException.class,
                () -> new ProviderService(listings, enquiries).update(
                        account,
                        listing.getId(),
                        new ProviderRequest(
                                "Example Plumbing", "Plumbing", "Midrand", "Edited pipe repairs",
                                null, null, List.of("Pipe repairs"), "0000000000", "provider@example.com")));

        assertEquals(409, error.getStatusCode().value());
    }
}
