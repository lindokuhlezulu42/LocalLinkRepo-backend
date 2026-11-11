package za.co.locallink.provider;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/providers")
public class AdminProviderController {
    private final ProviderService providers;

    public AdminProviderController(ProviderService providers) {
        this.providers = providers;
    }

    @GetMapping
    public List<ProviderResponse> all() {
        return providers.findAllForAdmin();
    }

    @GetMapping("/pending")
    public List<ProviderResponse> pending() {
        return providers.findPending();
    }

    @PatchMapping("/{id}/status")
    public ProviderResponse updateStatus(
            @PathVariable UUID id,
            @RequestBody StatusRequest request) {
        return providers.updateStatus(id, request.status());
    }

    @PatchMapping("/{id}/approve-changes")
    public ProviderResponse approveResubmittedChanges(@PathVariable UUID id) {
        return providers.approveResubmittedChanges(id);
    }

    @PatchMapping("/{id}/reject-changes")
    public ProviderResponse rejectResubmittedChanges(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRejectionRequest request) {
        return providers.rejectResubmittedChanges(id, request.reason());
    }

    @DeleteMapping("/{id}")
    public void deleteAfterProviderRequest(@PathVariable UUID id) {
        providers.deleteAfterProviderRequest(id);
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record ChangeRejectionRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
