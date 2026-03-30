package ai.workley.core.auth;

import ai.workley.core.chat.TestRunner;
import ai.workley.core.auth.model.AuthenticationRequest.*;
import ai.workley.core.auth.model.AuthenticationResponse.*;
import ai.workley.core.auth.service.SendGridEmailService;
import ai.workley.core.auth.service.SocialLoginService;
import ai.workley.core.auth.service.SocialLoginService.SocialLoginResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class AuthenticationControllerIT extends TestRunner {
    private static final String AUTH_URL = "/api/auth";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @MockitoBean
    private SendGridEmailService sendGridEmailService;

    @Autowired
    private SocialLoginService socialLoginService;

    private final ArgumentCaptor<String> otpCodeCaptor = ArgumentCaptor.forClass(String.class);

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
        when(sendGridEmailService.sendOtp(anyString(), otpCodeCaptor.capture())).thenReturn(Mono.empty());
    }

    private String getLastCapturedOtp() {
        return otpCodeCaptor.getValue();
    }

    @Test
    void continueWithNewEmail_shouldReturnRegister() {
        ContinueResponse response = webTestClient.post().uri(AUTH_URL + "/continue")
                .bodyValue(new ContinueRequest("newuser@example.com"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ContinueResponse.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        assertEquals("register", response.nextStep());
    }

    @Test
    void continueWithInvalidEmail_shouldReturn400() {
        webTestClient.post().uri(AUTH_URL + "/continue")
                .bodyValue(new ContinueRequest("not-an-email"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void fullRegistrationFlow() {
        String email = "register-test@example.com";

        ContinueResponse continueResp = webTestClient.post().uri(AUTH_URL + "/continue")
                .bodyValue(new ContinueRequest(email))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ContinueResponse.class)
                .returnResult().getResponseBody();

        assertEquals("register", continueResp.nextStep());

        StepResponse registerResp = webTestClient.post().uri(AUTH_URL + "/register")
                .bodyValue(new RegisterRequest(email, "password123", "password123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(StepResponse.class)
                .returnResult().getResponseBody();

        assertNotNull(registerResp);
        assertEquals("verify_otp", registerResp.nextStep());
        assertNotNull(registerResp.preAuthToken());

        String otp = getLastCapturedOtp();
        assertNotNull(otp);
        assertEquals(6, otp.length());

        webTestClient.post().uri(AUTH_URL + "/verify-otp")
                .bodyValue(new VerifyOtpRequest(registerResp.preAuthToken(), otp))
                .exchange()
                .expectStatus().isOk()
                .expectCookie().exists("accessToken")
                .expectCookie().exists("refreshToken");
    }

    @Test
    void fullLoginFlow() {
        String email = "login-test@example.com";

        StepResponse registerResp = webTestClient.post().uri(AUTH_URL + "/register")
                .bodyValue(new RegisterRequest(email, "password123", "password123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(StepResponse.class)
                .returnResult().getResponseBody();

        String otp = getLastCapturedOtp();
        webTestClient.post().uri(AUTH_URL + "/verify-otp")
                .bodyValue(new VerifyOtpRequest(registerResp.preAuthToken(), otp))
                .exchange()
                .expectStatus().isOk();

        ContinueResponse continueResp = webTestClient.post().uri(AUTH_URL + "/continue")
                .bodyValue(new ContinueRequest(email))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ContinueResponse.class)
                .returnResult().getResponseBody();

        assertEquals("login", continueResp.nextStep());

        // Login — user is CREATED with incomplete onboarding (PERSONAL_INFORMATION)
        // Backend returns StepResponse with next step name and issues tokens
        StepResponse loginResp = webTestClient.post().uri(AUTH_URL + "/login")
                .bodyValue(new LoginRequest(email, "password123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(StepResponse.class)
                .returnResult().getResponseBody();

        assertNotNull(loginResp);
        assertEquals("PERSONAL_INFORMATION", loginResp.nextStep());
    }

    @Test
    void wrongPassword_shouldReturn401() {
        String email = "wrong-pass@example.com";

        webTestClient.post().uri(AUTH_URL + "/register")
                .bodyValue(new RegisterRequest(email, "password123", "password123"))
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri(AUTH_URL + "/login")
                .bodyValue(new LoginRequest(email, "wrongpassword"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void wrongOtp_shouldReturn401() {
        StepResponse registerResp = webTestClient.post().uri(AUTH_URL + "/register")
                .bodyValue(new RegisterRequest("otp-test@example.com", "password123", "password123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(StepResponse.class)
                .returnResult().getResponseBody();

        webTestClient.post().uri(AUTH_URL + "/verify-otp")
                .bodyValue(new VerifyOtpRequest(registerResp.preAuthToken(), "000000"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void meWithoutToken_shouldReturn401() {
        webTestClient.get().uri(AUTH_URL + "/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void meWithValidToken_shouldReturnEmail() {
        String email = "me-test@example.com";

        StepResponse registerResp = webTestClient.post().uri(AUTH_URL + "/register")
                .bodyValue(new RegisterRequest(email, "password123", "password123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(StepResponse.class)
                .returnResult().getResponseBody();

        String otp = getLastCapturedOtp();
        ResponseCookie accessCookie = webTestClient.post().uri(AUTH_URL + "/verify-otp")
                .bodyValue(new VerifyOtpRequest(registerResp.preAuthToken(), otp))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class)
                .getResponseCookies().getFirst("accessToken");

        assertNotNull(accessCookie);

        MeResponse meResp = webTestClient.get().uri(AUTH_URL + "/me")
                .cookie("accessToken", accessCookie.getValue())
                .exchange()
                .expectStatus().isOk()
                .expectBody(MeResponse.class)
                .returnResult().getResponseBody();

        assertNotNull(meResp);
        assertEquals(email, meResp.email());
    }

    @Test
    void logout_shouldClearCookies() {
        String email = "logout-test@example.com";

        StepResponse registerResp = webTestClient.post().uri(AUTH_URL + "/register")
                .bodyValue(new RegisterRequest(email, "password123", "password123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(StepResponse.class)
                .returnResult().getResponseBody();

        String otp = getLastCapturedOtp();
        var verifyResult = webTestClient.post().uri(AUTH_URL + "/verify-otp")
                .bodyValue(new VerifyOtpRequest(registerResp.preAuthToken(), otp))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class);

        ResponseCookie refreshCookie =
                verifyResult.getResponseCookies().getFirst("refreshToken");
        assertNotNull(refreshCookie);

        webTestClient.post().uri(AUTH_URL + "/logout")
                .cookie("refreshToken", refreshCookie.getValue())
                .exchange()
                .expectStatus().isOk()
                .expectCookie().maxAge("accessToken", java.time.Duration.ZERO)
                .expectCookie().maxAge("refreshToken", java.time.Duration.ZERO);
    }

    @Test
    void loginWithNoPassword_shouldReturnNoPasswordSet() {
        socialLoginService.authenticate(
                "google", "google-no-pass", "no-pass@example.com", true, "No Pass User").block();

        webTestClient.post().uri(AUTH_URL + "/login")
                .bodyValue(new LoginRequest("no-pass@example.com", "anypassword"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("no_password_set");
    }

    @Test
    void mergedUser_canStillLoginWithPassword() {
        String email = "can-still-login@example.com";

        StepResponse registerResp = webTestClient.post().uri(AUTH_URL + "/register")
                .bodyValue(new RegisterRequest(email, "password123", "password123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(StepResponse.class)
                .returnResult().getResponseBody();

        String otp = getLastCapturedOtp();
        webTestClient.post().uri(AUTH_URL + "/verify-otp")
                .bodyValue(new VerifyOtpRequest(registerResp.preAuthToken(), otp))
                .exchange()
                .expectStatus().isOk();

        SocialLoginResult mergeResult = socialLoginService.authenticate(
                "google", "google-merge-login", email, true, "Merged User").block();
        assertNotNull(mergeResult);

        webTestClient.post().uri(AUTH_URL + "/login")
                .bodyValue(new LoginRequest(email, "password123"))
                .exchange()
                .expectStatus().isOk();
    }
}
