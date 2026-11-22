package za.co.locallink.appointment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import za.co.locallink.provider.ProviderListing;

@Entity
@Table(name = "appointment_slots")
public class AppointmentSlot {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderListing provider;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean available = true;

    protected AppointmentSlot() {}

    public AppointmentSlot(ProviderListing provider, Instant startAt, Instant endAt) {
        this.provider = provider;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    @PrePersist
    void onCreate() { if (id == null) id = UUID.randomUUID(); }

    public UUID getId() { return id; }
    public ProviderListing getProvider() { return provider; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public boolean isActive() { return active; }
    public boolean isAvailable() { return active && available; }
    public void setActive(boolean active) { this.active = active; }
    public void setAvailable(boolean available) { this.available = available; }
}
