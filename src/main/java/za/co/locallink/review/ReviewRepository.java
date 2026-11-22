package za.co.locallink.review;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    boolean existsByEnquiryId(UUID enquiryId);
    List<Review> findByProviderIdOrderByCreatedAtDesc(UUID providerId);
    @Modifying
    @Query("delete from Review review where review.provider.id = :providerId")
    void deleteAllByProviderId(@Param("providerId") UUID providerId);

    @Modifying
    @Query("delete from Review review where review.enquiry.id = :enquiryId")
    void deleteAllByEnquiryId(@Param("enquiryId") UUID enquiryId);

    @Query("select r.provider.id, avg(r.rating), count(r) from Review r where r.provider.id in :providerIds group by r.provider.id")
    List<Object[]> summarizeByProviderIds(@Param("providerIds") Collection<UUID> providerIds);

    @Query("select avg(r.rating) from Review r where r.provider.id = :providerId")
    Double averageRatingFor(@Param("providerId") UUID providerId);

    @Query("select count(r) from Review r where r.provider.id = :providerId")
    long countByProviderId(@Param("providerId") UUID providerId);
}
