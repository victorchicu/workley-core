package ai.workley.core.auth.controller;

import ai.workley.core.auth.config.CookieProperties;
import ai.workley.core.auth.config.AuthenticatedJwtWebFilter;
import ai.workley.core.auth.model.AuthenticationError;
import ai.workley.core.auth.model.AuthenticationRequest.*;
import ai.workley.core.auth.model.AuthenticationResponse;
import ai.workley.core.auth.model.AuthenticationResponse.MeResponse;
import ai.workley.core.auth.model.OnboardingStepType;
import ai.workley.core.auth.model.UserStatus;
import ai.workley.core.auth.repository.R2dbcUserProfileRepository;
import ai.workley.core.auth.repository.R2dbcUserRepository;
import ai.workley.core.auth.repository.UserProfileEntity;
import ai.workley.core.auth.service.AuthenticationService;
import ai.workley.core.auth.service.OnboardingService;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final CookieProperties cookieProperties;
    private final OnboardingService onboardingService;
    private final AuthenticationService authenticationService;
    private final R2dbcUserRepository userRepository;
    private final R2dbcUserProfileRepository userProfileRepository;

    public AuthenticationController(
            CookieProperties cookieProperties,
            OnboardingService onboardingService,
            AuthenticationService authenticationService,
            R2dbcUserRepository userRepository,
            R2dbcUserProfileRepository userProfileRepository
    ) {
        this.cookieProperties = cookieProperties;
        this.onboardingService = onboardingService;
        this.authenticationService = authenticationService;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<AuthenticationResponse>> me() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication ->
                        authentication != null && authentication.isAuthenticated()
                                && authentication.getPrincipal() instanceof AuthenticatedJwtWebFilter.AuthenticatedPrincipal
                )
                .<ResponseEntity<AuthenticationResponse>>map(auth -> {
                    AuthenticatedJwtWebFilter.AuthenticatedPrincipal principal
                            = (AuthenticatedJwtWebFilter.AuthenticatedPrincipal) auth.getPrincipal();
                    return ResponseEntity.ok(new MeResponse(principal.email()));
                })
                .defaultIfEmpty(ResponseEntity.status(401).build());
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthenticationResponse>> login(@RequestBody LoginRequest request, ServerHttpResponse response) {
        return authenticationService.login(request.email(), request.password(), response)
                .<ResponseEntity<AuthenticationResponse>>map(ResponseEntity::ok)
                .onErrorResume(AuthenticationError.class, this::handleError);
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<AuthenticationResponse>> logout(ServerHttpRequest request, ServerHttpResponse response) {
        String refreshToken = Optional.ofNullable(request.getCookies().getFirst(cookieProperties.refreshTokenCookieName()))
                .map(HttpCookie::getValue)
                .orElse(null);
        return authenticationService.logout(refreshToken, response)
                .<ResponseEntity<AuthenticationResponse>>thenReturn(ResponseEntity.ok().build())
                .onErrorResume(Exception.class, e ->
                        Mono.just(ResponseEntity.internalServerError().build()));
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthenticationResponse>> register(@RequestBody RegisterRequest request) {
        return authenticationService.register(request.email(), request.password(), request.passwordConfirmation())
                .<ResponseEntity<AuthenticationResponse>>map(ResponseEntity::ok)
                .onErrorResume(AuthenticationError.class, this::handleError);
    }

    @PostMapping("/verify-otp")
    public Mono<ResponseEntity<AuthenticationResponse>> verifyOtp(@RequestBody VerifyOtpRequest request, ServerHttpResponse response) {
        return authenticationService.verifyOtp(request.preAuthToken(), request.otp(), response)
                .<ResponseEntity<AuthenticationResponse>>thenReturn(ResponseEntity.ok().build())
                .onErrorResume(AuthenticationError.class, this::handleError);
    }

    @PostMapping("/continue")
    public Mono<ResponseEntity<AuthenticationResponse>> continueAuth(@RequestBody ContinueRequest request) {
        return authenticationService.continueOnboarding(request.email())
                .<ResponseEntity<AuthenticationResponse>>map(ResponseEntity::ok)
                .onErrorResume(AuthenticationError.class, this::handleError);
    }

    @PostMapping("/complete")
    public Mono<ResponseEntity<AuthenticationResponse>> completeProfile(@RequestBody CompleteProfileRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication ->
                        authentication != null && authentication.isAuthenticated()
                                && authentication.getPrincipal() instanceof AuthenticatedJwtWebFilter.AuthenticatedPrincipal
                )
                .map(authentication -> (AuthenticatedJwtWebFilter.AuthenticatedPrincipal) authentication.getPrincipal())
                .flatMap(principal -> completeProfileForUser(principal, request))
                .defaultIfEmpty(ResponseEntity.status(401).build())
                .onErrorResume(AuthenticationError.class, this::handleError);
    }


    private Mono<ResponseEntity<AuthenticationResponse>> completeProfileForUser(
            AuthenticatedJwtWebFilter.AuthenticatedPrincipal principal,
            CompleteProfileRequest request
    ) {
        if (request.fullName() == null || request.fullName().isBlank()) {
            return Mono.error(AuthenticationError.invalidFullName());
        }
        if (request.age() < 18) {
            return Mono.error(AuthenticationError.underage());
        }
        UUID userId = UUID.fromString(principal.userId());
        return userProfileRepository.findByUserId(userId)
                .flatMap(existing -> {
                    if (existing.getAge() > 0) {
                        return Mono.<UserProfileEntity>error(AuthenticationError.profileAlreadyCompleted());
                    }
                    existing.setFullName(request.fullName().trim());
                    existing.setAge(request.age());
                    existing.setUpdatedAt(Instant.now());
                    return userProfileRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    UserProfileEntity profile = new UserProfileEntity()
                            .setUserId(userId)
                            .setFullName(request.fullName().trim())
                            .setAge(request.age())
                            .setCreatedAt(Instant.now())
                            .setUpdatedAt(Instant.now());
                    return userProfileRepository.save(profile);
                }))
                .then(onboardingService.markStepCompleted(userId, OnboardingStepType.PERSONAL_INFORMATION))
                .then(onboardingService.isFullyOnboarded(userId))
                .flatMap(fullyOnboarded -> {
                    if (fullyOnboarded) {
                        return userRepository.findById(userId)
                                .flatMap(user -> {
                                    user.setStatus(UserStatus.ACTIVE.name());
                                    user.setUpdatedAt(Instant.now());
                                    return userRepository.save(user);
                                }).then();
                    }
                    return Mono.empty();
                })
                .thenReturn(ResponseEntity.ok().<AuthenticationResponse>build());
    }

    private Mono<ResponseEntity<AuthenticationResponse>> handleError(AuthenticationError error) {
        if ("onboarding_incomplete".equals(error.getErrorCode())) {
            return Mono.just(ResponseEntity.status(error.getStatus())
                    .body(new AuthenticationResponse.OnboardingIncompleteResponse(
                            error.getErrorCode(), "Onboarding incomplete", error.getMessage())));
        }
        return Mono.just(ResponseEntity.status(error.getStatus())
                .body(new AuthenticationResponse.AuthenticationErrorResponse(error.getErrorCode(), error.getMessage())));
    }
}