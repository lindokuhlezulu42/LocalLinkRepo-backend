package za.co.locallink.enquiry;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import za.co.locallink.account.UserAccount;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class EnquiryController {
    private final EnquiryService enquiries;

    public EnquiryController(EnquiryService enquiries) {
        this.enquiries = enquiries;
    }

    @PostMapping("/providers/{providerId}/enquiries")
    public EnquiryResponse create(
            @PathVariable UUID providerId,
            @AuthenticationPrincipal UserAccount resident,
            @Valid @RequestBody EnquiryRequest request) {
        return enquiries.create(providerId, resident, request);
    }

    @GetMapping("/enquiries/mine")
    public List<EnquiryResponse> mine(@AuthenticationPrincipal UserAccount account) {
        return enquiries.findMine(account);
    }

    @DeleteMapping("/enquiries/{id}")
    public void hide(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserAccount account) {
        enquiries.hideForProvider(id, account);
    }
}
