package za.co.locallink;

import java.util.UUID;
import java.util.Properties;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.ArgumentCaptor;
import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.Session;

import za.co.locallink.provider.ProviderService;
import za.co.locallink.provider.ProviderListing;
import za.co.locallink.provider.ProviderListingRepository;
import za.co.locallink.provider.ListingStatus;
import za.co.locallink.account.EmailVerificationTokenRepository;
import za.co.locallink.account.AdminBootstrap;
import za.co.locallink.account.AccountRole;
import za.co.locallink.account.UserAccount;
import za.co.locallink.account.UserAccountRepository;
import za.co.locallink.enquiry.EnquiryResponse;
import za.co.locallink.enquiry.Enquiry;
import za.co.locallink.enquiry.EnquiryRepository;
import za.co.locallink.enquiry.EnquiryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalLinkApiApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private ProviderService providerService;

	@MockBean
	private EnquiryService enquiryService;

	@MockBean
	private JavaMailSender mailSender;

	@Autowired
	private EmailVerificationTokenRepository verificationTokens;

	@Autowired
	private ProviderListingRepository providerListings;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private EnquiryRepository enquiryRepository;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void adminBootstrapReconcilesExistingAccountWithConfiguredCredentials() {
		var email = "bootstrap-" + UUID.randomUUID() + "@example.com";
		var existingAccount = accounts.save(new UserAccount(
				"Existing User",
				email,
				passwordEncoder.encode("an-old-password"),
				AccountRole.PROVIDER));
		existingAccount.setEmailVerified(false);
		accounts.save(existingAccount);

		var configuredPassword = "a-new-configured-password";
		new AdminBootstrap(accounts, passwordEncoder, email, configuredPassword).run(null);

		var configuredAccount = accounts.findByEmailIgnoreCase(email).orElseThrow();
		assertEquals(AccountRole.ADMIN, configuredAccount.getRole());
		assertTrue(passwordEncoder.matches(configuredPassword, configuredAccount.getPassword()));
		assertTrue(configuredAccount.isEmailVerified());
	}

	@Test
	void publicProviderDirectoryIsAvailableWithoutAuthentication() throws Exception {
		when(providerService.search(null, null, null, null)).thenReturn(java.util.List.of());

		mockMvc.perform(get("/api/providers"))
				.andExpect(status().isOk());
	}

	@Test
	void ownedProviderListingsRemainProtectedWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/providers/mine"))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminListingAndEnquiryQueuesRequireAdministratorRole() throws Exception {
		mockMvc.perform(get("/api/admin/providers"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/enquiries"))
				.andExpect(status().isForbidden());

		when(providerService.findAllForAdmin()).thenReturn(java.util.List.of());
		mockMvc.perform(get("/api/admin/providers").with(user("admin").roles("ADMIN")))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/admin/enquiries").with(user("admin").roles("ADMIN")))
				.andExpect(status().isOk());
	}

	@Test
	void administratorsCannotCreateOrEditListings() throws Exception {
		var listingId = UUID.randomUUID();
		var request = """
				{"name":"Admin Listing","category":"Plumbing","area":"Midrand","description":"Listing details","price":"Quotes on request","experience":"5 years","services":["Pipe repairs"],"phone":"0000000000","email":"admin@example.com"}
				""";

		mockMvc.perform(post("/api/providers")
						.with(user("admin").roles("ADMIN"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isForbidden());

		mockMvc.perform(patch("/api/admin/providers/" + listingId)
						.with(user("admin").roles("ADMIN"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isNotFound());
	}

	@Test
	void providersCanDeleteTheirOwnListingsWithoutAdminApproval() throws Exception {
		var listingId = UUID.randomUUID();
		var provider = new UserAccount(
				"Test Provider", "provider-" + UUID.randomUUID() + "@example.com",
				"not-a-real-password-hash", AccountRole.PROVIDER);

		mockMvc.perform(delete("/api/providers/" + listingId).with(csrf()))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/providers/" + listingId)
						.with(user("admin").roles("ADMIN"))
						.with(csrf()))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/providers/" + listingId)
						.with(user(provider))
						.with(csrf()))
				.andExpect(status().isNoContent());
		verify(providerService).deleteOwned(provider, listingId);
	}

	@Test
	void adminEnquiryActionsRequireAdministratorRole() throws Exception {
		var enquiryId = UUID.randomUUID();
		when(enquiryService.setReadByAdmin(enquiryId, true)).thenReturn(new EnquiryResponse(
				enquiryId, UUID.randomUUID(), "Test provider", null, "Test resident",
				"0000000000", null, "Test enquiry", "new", Instant.now(), true));
		mockMvc.perform(patch("/api/admin/enquiries/" + enquiryId + "/read")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"read\":true}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(patch("/api/admin/enquiries/" + enquiryId + "/read")
						.with(user("admin").roles("ADMIN"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"read\":true}"))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/admin/enquiries/" + enquiryId)
						.with(csrf()))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/enquiries/" + enquiryId)
						.with(user("admin").roles("ADMIN"))
						.with(csrf()))
				.andExpect(status().isNoContent());
	}

	@Test
	void onlyProvidersCanEditListings() throws Exception {
		var listingId = UUID.randomUUID();
		var request = """
				{"name":"Example Plumbing","category":"Plumbing","area":"Midrand",
				"description":"Pipe repairs","price":"","experience":"",
				"services":["Pipe repairs"],"phone":"0000000000","email":"provider@example.com"}
				""";
		mockMvc.perform(patch("/api/providers/" + listingId)
						.with(user("resident").roles("RESIDENT"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/providers/" + listingId)
						.with(user("provider").roles("PROVIDER"))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isOk());
	}

	@Test
	@Transactional
	void providerDirectorySearchMatchesPartialAreaAndTextCaseInsensitively() {
		var owner = accounts.save(new UserAccount(
				"Filter Test Provider",
				"filter-" + UUID.randomUUID() + "@example.com",
				"not-a-real-password-hash",
				AccountRole.PROVIDER));
		var listing = new ProviderListing(
				owner,
				"Bright Plumbing Works",
				"Plumbing",
				"Midrand Central",
				"Emergency pipe repairs and installations",
				null,
				null,
				java.util.List.of("Leak detection"),
				"0000000000",
				"filter@example.com");
		listing.setStatus(ListingStatus.APPROVED);
		providerListings.save(listing);

		var matches = providerListings.searchApproved(
				ListingStatus.APPROVED, "plumbing", "%midrand%", "%leak det%");

		assertEquals(1, matches.size());
		assertEquals(listing.getId(), matches.get(0).getId());
	}

	@Test
	@Transactional
	void adminCanDeleteReadEnquiryFromDatabase() {
		var owner = accounts.save(new UserAccount(
				"Enquiry Delete Provider",
				"enquiry-delete-" + UUID.randomUUID() + "@example.com",
				"not-a-real-password-hash",
				AccountRole.PROVIDER));
		var listing = providerListings.save(new ProviderListing(
				owner,
				"Enquiry Delete Plumbing",
				"Plumbing",
				"Midrand",
				"Pipe repairs",
				null,
				null,
				java.util.List.of("Pipe repairs"),
				"0000000000",
				"enquiry-delete@example.com"));
		var enquiry = new Enquiry(listing, null, "Resident", "0000000000", null, "Please call me.");
		enquiry.setReadByAdmin(true);
		var savedEnquiry = enquiryRepository.saveAndFlush(enquiry);

		assertEquals(1, enquiryRepository.deleteReadById(savedEnquiry.getId()));
		assertTrue(enquiryRepository.findById(savedEnquiry.getId()).isEmpty());
	}

	@Test
	void newAccountsMustVerifyTheirEmailBeforeSigningIn() throws Exception {
		var email = "verification-" + UUID.randomUUID() + "@example.com";
		when(mailSender.createMimeMessage()).thenReturn(
				new MimeMessage(Session.getInstance(new Properties())));
		mockMvc.perform(post("/api/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Test Resident","email":"%s","password":"secure-pass-123","role":"resident"}
								""".formatted(email)))
				.andExpect(status().isOk());

		var message = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(message.capture());
		var matcher = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)")
				.matcher(extractMessageText(message.getValue().getContent()));
		assertTrue(matcher.find());
		assertTrue(extractMessageText(message.getValue().getContent()).contains("This link expires in 10 minutes."));
		assertTrue(extractMessageText(message.getValue().getContent()).contains("Yes, verify my email"));
		var token = matcher.group(1);
		var tokenHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(token.getBytes(StandardCharsets.UTF_8)));
		var expiresAt = verificationTokens.findById(tokenHash).orElseThrow().getExpiresAt();
		var checkedAt = Instant.now();
		assertTrue(expiresAt.isAfter(checkedAt));
		assertTrue(!expiresAt.isAfter(checkedAt.plusSeconds(600)));

		mockMvc.perform(post("/api/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"secure-pass-123"}
								""".formatted(email)))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/auth/verify-email")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"%s"}
								""".formatted(token)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/auth/verify-email")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"%s"}
								""".formatted(token)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"secure-pass-123"}
								""".formatted(email)))
				.andExpect(status().isOk());
	}

	@Test
	void forgottenPasswordsCanBeResetOnceThroughTheEmailedLink() throws Exception {
		var email = "reset-" + UUID.randomUUID() + "@example.com";
		accounts.save(new UserAccount("Reset Resident", email,
				passwordEncoder.encode("old-pass-123"), AccountRole.RESIDENT));
		when(mailSender.createMimeMessage()).thenReturn(
				new MimeMessage(Session.getInstance(new Properties())));

		// Unknown and admin addresses get the same answer but no email.
		for (var address : new String[] {"nobody-" + UUID.randomUUID() + "@example.com", "admin@test.local"}) {
			mockMvc.perform(post("/api/auth/forgot-password")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"%s"}
									""".formatted(address)))
					.andExpect(status().isOk());
		}
		verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

		mockMvc.perform(post("/api/auth/forgot-password")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s"}
								""".formatted(email.toUpperCase())))
				.andExpect(status().isOk());

		var message = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(message.capture());
		var text = extractMessageText(message.getValue().getContent());
		var matcher = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)").matcher(text);
		assertTrue(matcher.find());
		var resetBody = """
				{"token":"%s","password":"new-pass-456"}
				""".formatted(matcher.group(1));

		mockMvc.perform(post("/api/auth/reset-password")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(resetBody))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/auth/reset-password")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(resetBody))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"old-pass-123"}
								""".formatted(email)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"new-pass-456"}
								""".formatted(email)))
				.andExpect(status().isOk());
	}

	private static String extractMessageText(Object content) throws Exception {
		if (content instanceof String text) return text;
		if (content instanceof MimeMultipart multipart) {
			var text = new StringBuilder();
			for (int index = 0; index < multipart.getCount(); index++) {
				BodyPart part = multipart.getBodyPart(index);
				text.append(extractMessageText(part.getContent()));
			}
			return text.toString();
		}
		return "";
	}

}
