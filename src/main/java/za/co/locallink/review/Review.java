package za.co.locallink.review;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import za.co.locallink.account.UserAccount;
import za.co.locallink.enquiry.Enquiry;
import za.co.locallink.provider.ProviderListing;

@Entity
@Table(name = "provider_reviews", uniqueConstraints = @UniqueConstraint(columnNames = "enquiry_id"))
public class Review {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enquiry_id", nullable = false)
    private Enquiry enquiry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderListing provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private UserAccount resident;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {}

    public Review(Enquiry enquiry, int rating, String comment) {
        this.enquiry = enquiry;
        this.provider = enquiry.getProvider();
        this.resident = enquiry.getResident();
        this.rating = rating;
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Enquiry getEnquiry() { return enquiry; }
    public ProviderListing getProvider() { return provider; }
    public UserAccount getResident() { return resident; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
