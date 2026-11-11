package za.co.locallink.provider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderListingRepository extends JpaRepository<ProviderListing, UUID> {

    List<ProviderListing> findByStatusOrderByCreatedAtDesc(ListingStatus status);

    List<ProviderListing> findAllByOrderByCreatedAtDesc();

    List<ProviderListing> findByOwner_IdOrderByCreatedAtDesc(UUID ownerId);

    Optional<ProviderListing> findByIdAndStatus(UUID id, ListingStatus status);

    @Query("""
            select distinct listing from ProviderListing listing
            left join listing.services service
            where listing.status = :status
              and (:category is null or lower(listing.category) = :category)
              and (:areaPattern is null or lower(listing.area) like :areaPattern)
              and (:queryPattern is null or
                   lower(listing.name) like :queryPattern or
                   lower(listing.category) like :queryPattern or
                   lower(listing.area) like :queryPattern or
                   lower(listing.description) like :queryPattern or
                   lower(service) like :queryPattern)
            order by listing.createdAt desc
            """)
    List<ProviderListing> searchApproved(
            @Param("status") ListingStatus status,
            @Param("category") String category,
            @Param("areaPattern") String areaPattern,
            @Param("queryPattern") String queryPattern);

    @Query("""
            select listing from ProviderListing listing
            where listing.owner.id = :ownerId
              and (:status is null or listing.status = :status)
            order by listing.createdAt desc
            """)
    List<ProviderListing> findOwned(
            @Param("ownerId") UUID ownerId,
            @Param("status") ListingStatus status);
}