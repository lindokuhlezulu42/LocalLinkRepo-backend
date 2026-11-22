package za.co.locallink.appointment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, UUID> {
    List<BookingRequest> findByResidentIdOrderByCreatedAtDesc(UUID residentId);
    List<BookingRequest> findBySlot_Provider_Owner_IdOrderByCreatedAtDesc(UUID ownerId);
    List<BookingRequest> findBySlotIdAndStatusOrderByCreatedAtAsc(UUID slotId, String status);
    boolean existsBySlotIdAndResidentIdAndStatusIn(UUID slotId, UUID residentId, List<String> statuses);
    Optional<BookingRequest> findByIdAndSlot_Provider_Owner_Id(UUID id, UUID ownerId);

    @Modifying
    @Query("delete from BookingRequest b where b.slot.provider.id = :providerId")
    void deleteAllByProviderId(@Param("providerId") UUID providerId);
}
