package za.co.locallink.provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import za.co.locallink.account.UserAccount;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "provider_listings")
public class ProviderListing {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 120)
    private String area;

    @Column(nullable = false, length = 1600)
    private String description;

    @Column(length = 100)
    private String price;

    @Column(length = 60)
    private String experience;

    @ElementCollection
    @CollectionTable(name = "provider_services", joinColumns = @JoinColumn(name = "provider_id"))
    @Column(name = "service_name", nullable = false, length = 100)
    private List<String> services = new ArrayList<>();

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deletionRequested;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean changesResubmitted;

    @Column(length = 500)
    private String changeRejectionReason;

    @Column(length = 120)
    private String pendingName;

    @Column(length = 40)
    private String pendingCategory;

    @Column(length = 120)
    private String pendingArea;

    @Column(length = 1600)
    private String pendingDescription;

    @Column(length = 100)
    private String pendingPrice;

    @Column(length = 60)
    private String pendingExperience;

    @ElementCollection
    @CollectionTable(name = "provider_pending_services", joinColumns = @JoinColumn(name = "provider_id"))
    @Column(name = "service_name", nullable = false, length = 100)
    private List<String> pendingServices = new ArrayList<>();

    @Column(length = 40)
    private String pendingPhone;

    @Column(length = 254)
    private String pendingEmail;

    protected ProviderListing() {
    }

    public ProviderListing(
            UserAccount owner,
            String name,
            String category,
            String area,
            String description,
            String price,
            String experience,
            List<String> services,
            String phone,
            String email) {
        this.owner = owner;
        this.name = name;
        this.category = category;
        this.area = area;
        this.description = description;
        this.price = price;
        this.experience = experience;
        this.services = new ArrayList<>(services);
        this.phone = phone;
        this.email = email;
        this.status = ListingStatus.PENDING;
        this.deletionRequested = false;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UserAccount getOwner() { return owner; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getArea() { return area; }
    public String getDescription() { return description; }
    public String getPrice() { return price; }
    public String getExperience() { return experience; }
    public List<String> getServices() { return List.copyOf(services); }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public ListingStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isDeletionRequested() { return deletionRequested; }
    public boolean isChangesResubmitted() { return changesResubmitted; }
    public String getChangeRejectionReason() { return changeRejectionReason; }
    public String getPendingName() { return pendingName; }
    public String getPendingCategory() { return pendingCategory; }
    public String getPendingArea() { return pendingArea; }
    public String getPendingDescription() { return pendingDescription; }
    public String getPendingPrice() { return pendingPrice; }
    public String getPendingExperience() { return pendingExperience; }
    public List<String> getPendingServices() { return List.copyOf(pendingServices); }
    public String getPendingPhone() { return pendingPhone; }
    public String getPendingEmail() { return pendingEmail; }

    public void setStatus(ListingStatus status) {
        this.status = status;
        if (status == ListingStatus.APPROVED) {
            changesResubmitted = false;
            clearPendingChanges();
        }
    }
    public void setDeletionRequested(boolean deletionRequested) { this.deletionRequested = deletionRequested; }
    public void setChangesResubmitted(boolean changesResubmitted) { this.changesResubmitted = changesResubmitted; }

    public void stageChanges(
            String name,
            String category,
            String area,
            String description,
            String price,
            String experience,
            List<String> services,
            String phone,
            String email) {
        pendingName = name;
        pendingCategory = category;
        pendingArea = area;
        pendingDescription = description;
        pendingPrice = price;
        pendingExperience = experience;
        pendingServices = new ArrayList<>(services);
        pendingPhone = phone;
        pendingEmail = email;
        changeRejectionReason = null;
        changesResubmitted = true;
    }

    public void approveStagedChanges() {
        if (pendingName != null) {
            updateDetails(
                    pendingName,
                    pendingCategory,
                    pendingArea,
                    pendingDescription,
                    pendingPrice,
                    pendingExperience,
                    pendingServices,
                    pendingPhone,
                    pendingEmail);
        }
        clearPendingChanges();
        changesResubmitted = false;
        changeRejectionReason = null;
    }

    public void rejectStagedChanges(String reason) {
        clearPendingChanges();
        changesResubmitted = false;
        changeRejectionReason = reason;
    }

    public void updateDetails(
            String name,
            String category,
            String area,
            String description,
            String price,
            String experience,
            List<String> services,
            String phone,
            String email) {
        this.name = name;
        this.category = category;
        this.area = area;
        this.description = description;
        this.price = price;
        this.experience = experience;
        this.services = new ArrayList<>(services);
        this.phone = phone;
        this.email = email;
        this.status = ListingStatus.PENDING;
        this.changesResubmitted = false;
        clearPendingChanges();
        this.changeRejectionReason = null;
    }

    private void clearPendingChanges() {
        pendingName = null;
        pendingCategory = null;
        pendingArea = null;
        pendingDescription = null;
        pendingPrice = null;
        pendingExperience = null;
        pendingServices.clear();
        pendingPhone = null;
        pendingEmail = null;
    }
}
