package za.co.locallink.enquiry;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

public interface EnquiryRepository extends JpaRepository<Enquiry, UUID> {
    List<Enquiry> findAllByOrderByCreatedAtDesc();

    @Query("""
            select enquiry from Enquiry enquiry
            where enquiry.resident.id = :accountId
               or (enquiry.provider.owner.id = :accountId and enquiry.hiddenForProvider = false)
            order by enquiry.createdAt desc
            """)
    List<Enquiry> findVisibleToAccount(@Param("accountId") UUID accountId);

    java.util.Optional<Enquiry> findByIdAndProvider_Owner_Id(UUID id, UUID ownerId);

    @Modifying
    @Query("delete from Enquiry enquiry where enquiry.provider.id = :providerId")
    void deleteAllByProviderId(@Param("providerId") UUID providerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Enquiry enquiry where enquiry.id = :enquiryId and enquiry.readByAdmin = true")
    int deleteReadById(@Param("enquiryId") UUID enquiryId);
}
