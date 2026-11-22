package za.co.locallink.appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, UUID> {
    List<AppointmentSlot> findByProviderIdOrderByStartAtAsc(UUID providerId);

    @Query("select s from AppointmentSlot s where s.provider.id = :providerId and s.active = true and s.available = true and s.startAt > :now order by s.startAt asc")
    List<AppointmentSlot> findOpenFuture(@Param("providerId") UUID providerId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AppointmentSlot s where s.id = :id")
    Optional<AppointmentSlot> findByIdForUpdate(@Param("id") UUID id);

    Optional<AppointmentSlot> findByIdAndProvider_Owner_Id(UUID id, UUID ownerId);

    @Query("""
            select count(s) > 0 from AppointmentSlot s
            where s.provider.id = :providerId
              and s.active = true
              and s.endAt > :now
              and s.startAt < :endAt
              and s.endAt > :startAt
            """)
    boolean existsActiveOverlap(@Param("providerId") UUID providerId,
                                @Param("startAt") Instant startAt,
                                @Param("endAt") Instant endAt,
                                @Param("now") Instant now);

    @Modifying
    @Query("delete from AppointmentSlot s where s.provider.id = :providerId")
    void deleteAllByProviderId(@Param("providerId") UUID providerId);
}
