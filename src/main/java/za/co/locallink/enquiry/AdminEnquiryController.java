package za.co.locallink.enquiry;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/admin/enquiries")
public class AdminEnquiryController {
    private final EnquiryService enquiries;

    public AdminEnquiryController(EnquiryService enquiries) {
        this.enquiries = enquiries;
    }

    @GetMapping
    public List<EnquiryResponse> all() {
        return enquiries.findAllForAdmin();
    }

    @PatchMapping("/{id}/read")
    public EnquiryResponse setRead(
            @PathVariable UUID id,
            @Valid @RequestBody ReadRequest request) {
        return enquiries.setReadByAdmin(id, request.read());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRead(@PathVariable UUID id) {
        enquiries.deleteReadByAdmin(id);
        return ResponseEntity.noContent().build();
    }

    public record ReadRequest(@NotNull Boolean read) {
    }
}
