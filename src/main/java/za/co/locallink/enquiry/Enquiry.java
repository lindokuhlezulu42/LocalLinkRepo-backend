package za.co.locallink.enquiry;

import java.time.Instant;
import java.util.UUID;

import za.co.locallink.account.UserAccount;
import za.co.locallink.provider.ProviderListing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_enquiries")
public class Enquiry {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderListing provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id")
    private UserAccount resident;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(nullable = false, length = 1200)
    private String message;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean hiddenForProvider;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean readByAdmin;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Enquiry() {
    }

    public Enquiry(
            ProviderListing provider,
            UserAccount resident,
            String name,
            String phone,
            String email,
            String message) {
        this.provider = provider;
        this.resident = resident;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.message = message;
        this.status = "new";
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ProviderListing getProvider() { return provider; }
    public UserAccount getResident() { return resident; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public boolean isHiddenForProvider() { return hiddenForProvider; }
    public boolean isReadByAdmin() { return readByAdmin; }
    public Instant getCreatedAt() { return createdAt; }
    public void setHiddenForProvider(boolean hiddenForProvider) { this.hiddenForProvider = hiddenForProvider; }
    public void setReadByAdmin(boolean readByAdmin) { this.readByAdmin = readByAdmin; }
}
