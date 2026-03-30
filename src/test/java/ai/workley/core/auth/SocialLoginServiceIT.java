package ai.workley.core.auth;

import ai.workley.core.auth.service.SocialLoginService;
import ai.workley.core.auth.service.SocialLoginService.SocialLoginResult;
import ai.workley.core.auth.repository.R2dbcUserLinkedProviderRepository;
import ai.workley.core.auth.repository.R2dbcUserRepository;
import ai.workley.core.auth.service.AuthenticationService;
import ai.workley.core.auth.service.SendGridEmailService;
import ai.workley.core.chat.TestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class SocialLoginServiceIT extends TestRunner {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @MockitoBean
    private SendGridEmailService sendGridEmailService;

    @Autowired
    private SocialLoginService socialLoginService;

    @Autowired
    private R2dbcUserRepository userRepository;

    @Autowired
    private R2dbcUserLinkedProviderRepository linkedProviderRepository;

    @Autowired
    private AuthenticationService authenticationService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("gateway.sendgrid.api-key", () -> "test-key");
        registry.add("gateway.sendgrid.from-email", () -> "test@example.com");
        registry.add("gateway.sendgrid.from-name", () -> "Test");
    }

    @BeforeEach
    void setUpMocks() {
        when(sendGridEmailService.sendOtp(anyString(), anyString())).thenReturn(Mono.empty());
    }

    @Test
    void newGoogleUser_shouldCreateUserAndReturnProfile() {
        Mono<SocialLoginResult> result = socialLoginService.authenticate(
                "google", "google-sub-123", "newgoogle@example.com", true, "John Doe");

        StepVerifier.create(result)
                .assertNext(r -> {
                    assertNotNull(r.userId());
                    assertEquals("newgoogle@example.com", r.email());
                    assertEquals("profile", r.nextStep());
                })
                .verifyComplete();

        // Verify user was created
        StepVerifier.create(userRepository.findByEmail("newgoogle@example.com"))
                .assertNext(user -> {
                    assertNull(user.getPasswordHash());
                    assertEquals("CREATED", user.getStatus());
                })
                .verifyComplete();

        // Verify linked provider was created
        StepVerifier.create(linkedProviderRepository.findByProviderAndSubject("google", "google-sub-123"))
                .assertNext(lp -> assertEquals("newgoogle@example.com", lp.getEmail()))
                .verifyComplete();
    }

    @Test
    void existingGoogleUser_shouldReturnAuthenticated() {
        // First login creates the user
        SocialLoginResult first = socialLoginService.authenticate(
                "google", "google-sub-existing", "existing-google@example.com", true, "Jane Doe").block();
        assertNotNull(first);

        // Second login should find the linked provider
        Mono<SocialLoginResult> result = socialLoginService.authenticate(
                "google", "google-sub-existing", "existing-google@example.com", true, "Jane Doe");

        StepVerifier.create(result)
                .assertNext(r -> {
                    assertEquals(first.userId(), r.userId());
                    assertEquals("profile", r.nextStep());
                })
                .verifyComplete();
    }

    @Test
    void googleEmailMatchesExistingUser_shouldAutoMerge() {
        // Register a user with email+password
        String email = "merge-test@example.com";
        authenticationService.register(email, "password123", "password123").block();

        // Google login with same email should merge
        Mono<SocialLoginResult> result = socialLoginService.authenticate(
                "google", "google-sub-merge", email, true, "Merge User");

        StepVerifier.create(result)
                .assertNext(r -> {
                    assertEquals("profile", r.nextStep());
                    assertEquals(email, r.email());
                })
                .verifyComplete();

        // Verify linked provider was added to existing user
        StepVerifier.create(userRepository.findByEmail(email))
                .assertNext(user -> {
                    assertNotNull(user.getPasswordHash()); // still has password
                    StepVerifier.create(linkedProviderRepository.findByProviderAndSubject("google", "google-sub-merge"))
                            .assertNext(lp -> assertEquals(user.getId(), lp.getUserId()))
                            .verifyComplete();
                })
                .verifyComplete();
    }

    @Test
    void googleWithUnverifiedEmail_shouldNotMerge() {
        // Register a user with email+password
        String email = "unverified-test@example.com";
        authenticationService.register(email, "password123", "password123").block();

        // Google login with same email but unverified should fail to create
        // (email UNIQUE constraint prevents duplicate)
        Mono<SocialLoginResult> result = socialLoginService.authenticate(
                "google", "google-sub-unverified", email, false, "Unverified User");

        StepVerifier.create(result)
                .expectError()
                .verify();
    }
}
