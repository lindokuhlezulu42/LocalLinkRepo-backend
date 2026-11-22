package za.co.locallink.appointment;

import java.time.Instant;
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
public class AppointmentService {
    private final AppointmentSlotRepository slots;
    private final BookingRequestRepository bookings;
    private final ProviderListingRepository providers;

    public AppointmentService(AppointmentSlotRepository slots, BookingRequestRepository bookings,
                              ProviderListingRepository providers) {
        this.slots = slots;
        this.bookings = bookings;
        this.providers = providers;
    }

    @Transactional
    public AppointmentDtos.SlotResponse createSlot(UUID providerId, UserAccount provider, AppointmentDtos.SlotRequest request) {
        var listing = providers.findByIdAndOwner_Id(providerId, provider.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."));
        if (listing.getStatus() != ListingStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Availability can only be added to approved listings.");
        }
        validateSlot(request.startAt(), request.endAt());
        if (slots.existsActiveOverlap(providerId, request.startAt(), request.endAt(), Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The slot overlaps another active future availability slot.");
        }
        var slot = slots.save(new AppointmentSlot(listing, request.startAt(), request.endAt()));
        return slot(slot);
    }

    @Transactional(readOnly = true)
    public List<AppointmentDtos.SlotResponse> providerSlots(UserAccount provider) {
        var owned = providers.findByOwner_IdOrderByCreatedAtDesc(provider.getId());
        return owned.stream()
                .flatMap(listing -> slots.findByProviderIdOrderByStartAtAsc(listing.getId()).stream())
                .filter(AppointmentSlot::isActive)
                .map(this::slot)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentDtos.SlotResponse> publicSlots(UUID providerId) {
        providers.findByIdAndStatus(providerId, ListingStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approved provider not found."));
        return slots.findOpenFuture(providerId, Instant.now()).stream().map(this::slot).toList();
    }

    @Transactional
    public void deleteSlot(UUID slotId, UserAccount provider) {
        var slot = slots.findByIdAndProvider_Owner_Id(slotId, provider.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment slot not found."));
        slot.setActive(false);
        slots.save(slot);
    }

    @Transactional
    public AppointmentDtos.BookingResponse request(UUID slotId, UserAccount resident, AppointmentDtos.BookingRequestDto request) {
        if (resident == null || resident.getRole() != AccountRole.RESIDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only residents can request appointments.");
        }
        var slot = slots.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment slot not found."));
        if (!slot.isActive() || !slot.isAvailable() || slot.getStartAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The appointment slot is not available.");
        }
        if (bookings.existsBySlotIdAndResidentIdAndStatusIn(
                slotId, resident.getId(), List.of("pending", "confirmed"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active booking request already exists.");
        }
        var message = request.message() == null ? null : request.message().trim();
        var booking = bookings.save(new BookingRequest(slot, resident, message));
        slot.setAvailable(false);
        slots.save(slot);
        return booking(booking);
    }

    @Transactional(readOnly = true)
    public List<AppointmentDtos.BookingResponse> mine(UserAccount resident) {
        return bookings.findByResidentIdOrderByCreatedAtDesc(resident.getId()).stream().map(this::booking).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentDtos.BookingResponse> providerBookings(UserAccount provider) {
        return bookings.findBySlot_Provider_Owner_IdOrderByCreatedAtDesc(provider.getId()).stream().map(this::booking).toList();
    }

    @Transactional
    public AppointmentDtos.BookingResponse respond(UUID bookingId, String status, UserAccount provider) {
        var booking = bookings.findByIdAndSlot_Provider_Owner_Id(bookingId, provider.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking request not found."));
        if (!"pending".equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending booking requests can be responded to.");
        }
        var slot = slots.findByIdForUpdate(booking.getSlot().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment slot not found."));
        if ("confirmed".equals(status)) {
            if (!slot.isActive()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The appointment slot is inactive.");
            }
            if (!bookings.findBySlotIdAndStatusOrderByCreatedAtAsc(slot.getId(), "confirmed").isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The appointment slot has already been booked.");
            }
            booking.setStatus("confirmed");
            slot.setAvailable(false);
        } else if ("declined".equals(status)) {
            booking.setStatus("declined");
            if (bookings.findBySlotIdAndStatusOrderByCreatedAtAsc(slot.getId(), "confirmed").isEmpty()) {
                slot.setAvailable(true);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid booking status.");
        }
        slots.save(slot);
        return booking(bookings.save(booking));
    }

    private void validateSlot(Instant startAt, Instant endAt) {
        if (!startAt.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time must be in the future.");
        }
        if (!endAt.isAfter(startAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time.");
        }
    }

    private AppointmentDtos.SlotResponse slot(AppointmentSlot slot) {
        return new AppointmentDtos.SlotResponse(slot.getId(), slot.getProvider().getId(),
                slot.getStartAt(), slot.getEndAt(), slot.isAvailable());
    }

    private AppointmentDtos.BookingResponse booking(BookingRequest booking) {
        var slot = booking.getSlot();
        var resident = booking.getResident();
        return new AppointmentDtos.BookingResponse(
                booking.getId(), slot.getId(), slot.getProvider().getId(),
                slot.getProvider().getName(), resident.getId(), resident.getName(),
                slot.getStartAt(), slot.getEndAt(), booking.getMessage(), booking.getStatus());
    }
}
