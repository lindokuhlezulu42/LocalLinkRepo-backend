package za.co.locallink.appointment;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import za.co.locallink.account.UserAccount;

@RestController
public class AppointmentController {
    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) { this.appointments = appointments; }

    @GetMapping("/api/providers/{providerId}/availability")
    public List<AppointmentDtos.SlotResponse> publicSlots(@PathVariable UUID providerId) {
        return appointments.publicSlots(providerId);
    }

    @GetMapping("/api/availability/provider")
    public List<AppointmentDtos.SlotResponse> providerSlots(@AuthenticationPrincipal UserAccount provider) {
        return appointments.providerSlots(provider);
    }

    @PostMapping("/api/providers/{providerId}/availability")
    public AppointmentDtos.SlotResponse createSlot(
            @PathVariable UUID providerId,
            @AuthenticationPrincipal UserAccount provider,
            @Valid @RequestBody AppointmentDtos.SlotRequest request) {
        return appointments.createSlot(providerId, provider, request);
    }

    @DeleteMapping("/api/availability/{slotId}")
    public void deleteSlot(@PathVariable UUID slotId, @AuthenticationPrincipal UserAccount provider) {
        appointments.deleteSlot(slotId, provider);
    }

    @PostMapping("/api/availability/{slotId}/bookings")
    public AppointmentDtos.BookingResponse request(
            @PathVariable UUID slotId,
            @AuthenticationPrincipal UserAccount resident,
            @Valid @RequestBody AppointmentDtos.BookingRequestDto request) {
        return appointments.request(slotId, resident, request);
    }

    @GetMapping("/api/bookings/mine")
    public List<AppointmentDtos.BookingResponse> mine(@AuthenticationPrincipal UserAccount resident) {
        return appointments.mine(resident);
    }

    @GetMapping("/api/bookings/provider")
    public List<AppointmentDtos.BookingResponse> providerBookings(@AuthenticationPrincipal UserAccount provider) {
        return appointments.providerBookings(provider);
    }

    @PatchMapping("/api/bookings/{id}/status")
    public AppointmentDtos.BookingResponse respond(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserAccount provider,
            @Valid @RequestBody AppointmentDtos.BookingStatusRequest request) {
        return appointments.respond(id, request.status(), provider);
    }
}
