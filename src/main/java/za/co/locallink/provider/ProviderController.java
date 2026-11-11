package za.co.locallink.provider;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import za.co.locallink.account.UserAccount;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {
    private final ProviderService providers;

    public ProviderController(ProviderService providers) {
        this.providers = providers;
    }

    @GetMapping
    public List<ProviderResponse> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String area,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) Integer limit) {
        return providers.search(category, area, query, limit);
    }

    @GetMapping("/mine")
    public List<ProviderResponse> mine(
            @AuthenticationPrincipal UserAccount account,
            @RequestParam(required = false) String status) {
        return providers.findOwned(account, status);
    }

    @GetMapping("/{id}")
    public ProviderResponse get(@PathVariable UUID id) {
        return providers.getPublic(id);
    }

    @PostMapping
    public ProviderResponse create(
            @AuthenticationPrincipal UserAccount account,
            @Valid @RequestBody ProviderRequest request) {
        return providers.create(account, request);
    }

    @PatchMapping("/{id}")
    public ProviderResponse update(
            @AuthenticationPrincipal UserAccount account,
            @PathVariable UUID id,
            @Valid @RequestBody ProviderRequest request) {
        return providers.update(account, id, request);
    }

    @PatchMapping("/{id}/deletion-request")
    public ProviderResponse requestDeletion(
            @AuthenticationPrincipal UserAccount account,
            @PathVariable UUID id,
            @Valid @RequestBody DeletionRequest request) {
        return providers.requestDeletion(account, id, request.requested());
    }

    public record DeletionRequest(@NotNull Boolean requested) {
    }
}
