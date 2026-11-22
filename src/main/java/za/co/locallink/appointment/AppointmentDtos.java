package za.co.locallink.appointment;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AppointmentDtos {
    public record SlotRequest(@NotNull Instant startAt, @NotNull Instant endAt) {}
    public record SlotResponse(UUID id, UUID providerId, Instant startAt, Instant endAt, boolean available) {}
    public record BookingRequestDto(@Size(max = 1000) String message) {}
    public record BookingStatusRequest(@Pattern(regexp = "confirmed|declined") String status) {}
    public record BookingResponse(
            UUID id, UUID slotId, UUID providerId, String providerName,
            UUID residentId, String residentName, Instant startAt, Instant endAt,
            String message, String status) {}
}
