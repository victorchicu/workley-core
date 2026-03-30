package ai.workley.core.auth.service;

import ai.workley.core.auth.config.CookieProperties;
import ai.workley.core.auth.config.AuthenticationJwtSecret;
import ai.workley.core.auth.model.AuthenticationError;
import ai.workley.core.auth.model.AuthenticationResponse;
import ai.workley.core.auth.model.AuthenticationResponse.*;
import ai.workley.core.auth.model.OnboardingStepType;
import ai.workley.core.auth.model.UserStatus;
import ai.workley.core.auth.repository.R2dbcRefreshTokenRepository;
import ai.workley.core.auth.repository.R2dbcUserRepository;
import ai.workley.core.auth.repository.RefreshTokenEntity;
import ai.workley.core.auth.repository.UserEntity;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.boot.web.server.Cookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthenticationService {
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Duration ACCESS_TOKEN_EXPIRY = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_EXPIRY = Duration.ofDays(30);
    private static final Duration PRE_AUTH_TOKEN_EXPIRY = Duration.ofMinutes(5);
    private static final Duration COOKIE_MAX_AGE_ACCESS = Duration.ofMinutes(15);
    private static final Duration COOKIE_MAX_AGE_REFRESH = Duration.ofDays(30);

    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final OnboardingService onboardingService;
    private final R2dbcUserRepository userRepository;
    private final SendGridEmailService sendGridEmailService;
    private final AuthenticationJwtSecret jwtSecret;
    private final R2dbcRefreshTokenRepository refreshTokenRepository;
    private final CookieProperties cookieProperties;

    public AuthenticationService(
            OtpService otpService,
            PasswordEncoder passwordEncoder,
            OnboardingService onboardingService,
            R2dbcUserRepository userRepository,
            SendGridEmailService sendGridEmailService,
            AuthenticationJwtSecret jwtSecret,
            R2dbcRefreshTokenRepository refreshTokenRepository,
            CookieProperties cookieProperties
    ) {
        this.jwtSecret = jwtSecret;
        this.otpService = otpService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.cookieProperties = cookieProperties;
        this.onboardingService = onboardingService;
        this.sendGridEmailService = sendGridEmailService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public Mono<Void> logout(String refreshTokenValue, ServerHttpResponse response) {
        clearAuthCookies(response);

        if (refreshTokenValue == null) return Mono.empty();

        String tokenHash = hashToken(refreshTokenValue);
        return refreshTokenRepository.deleteByTokenHash(tokenHash);
    }

    public Mono<Void> verifyOtp(String preAuthToken, String otp, ServerHttpResponse response) {
        DecodedJWT decoded;
        try {
            decoded = JWT.require(jwtSecret.getAlgorithm()).build().verify(preAuthToken);
        } catch (JWTVerificationException e) {
            return Mono.error(AuthenticationError.invalidPreAuth());
        }

        if (!"pre_auth".equals(decoded.getClaim("stage").asString())) {
            return Mono.error(AuthenticationError.invalidPreAuth());
        }

        UUID userId = UUID.fromString(decoded.getSubject());
        String email = decoded.getClaim("email").asString();

        return otpService.verify(userId, otp)
                .then(onboardingService.markStepCompleted(userId, OnboardingStepType.OTP_VERIFICATION))
                .then(onboardingService.isFullyOnboarded(userId))
                .flatMap(fullyOnboarded -> {
                    if (fullyOnboarded) {
                        return userRepository.findById(userId)
                                .flatMap(user -> {
                                    user.setStatus(UserStatus.ACTIVE.name());
                                    user.setUpdatedAt(Instant.now());
                                    return userRepository.save(user);
                                })
                                .then(issueTokens(userId, email, response));
                    }
                    return issueTokens(userId, email, response);
                });
    }

    public Mono<Void> issueTokens(UUID userId, String email, ServerHttpResponse response) {
        return issueAccessToken(userId, email, response).then();
    }

    public Mono<String> tryRefreshAccessToken(String accessTokenValue, String refreshTokenValue, ServerHttpResponse response) {
        if (refreshTokenValue == null) return Mono.empty();
        String tokenHash = hashToken(refreshTokenValue);
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .flatMap(refreshToken -> {
                    if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
                        return refreshTokenRepository.deleteByTokenHash(tokenHash)
                                .then(Mono.<UserEntity>empty());
                    }
                    return userRepository.findById(refreshToken.getUserId());
                })
                .flatMap(user -> {
                    String oldHash = hashToken(refreshTokenValue);
                    return refreshTokenRepository.deleteByTokenHash(oldHash)
                            .then(issueAccessToken(user.getId(), user.getEmail(), response));
                });
    }

    public Mono<StepResponse> register(String email, String password, String passwordConfirmation) {
        String normalized = email.trim().toLowerCase();

        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            return Mono.error(AuthenticationError.invalidEmail());
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            return Mono.error(AuthenticationError.passwordTooShort());
        }

        if (!password.equals(passwordConfirmation)) {
            return Mono.error(AuthenticationError.passwordsMismatch());
        }

        UserEntity user = new UserEntity()
                .setEmail(normalized)
                .setPasswordHash(passwordEncoder.encode(password))
                .setStatus(UserStatus.CREATED.name())
                .setCreatedAt(Instant.now())
                .setUpdatedAt(Instant.now());

        return userRepository.existsByEmail(normalized)
                .flatMap(exists -> {
                    if (exists) return Mono.error(AuthenticationError.emailAlreadyExists());
                    return userRepository.save(user);
                })
                .flatMap(saved -> checkOtpRateLimit(saved.getEmail())
                        .then(onboardingService.initializeSteps(saved.getId()).then())
                        .then(otpService.invalidatePending(saved.getId()))
                        .then(otpService.create(saved.getId(), saved.getEmail()))
                        .flatMap(code -> sendGridEmailService.sendOtp(saved.getEmail(), code)
                                .thenReturn(new StepResponse("verify_otp",
                                        createPreAuthToken(saved.getId(), saved.getEmail()))))
                );
    }

    public Mono<ContinueResponse> continueOnboarding(String email) {
        String normalized = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            return Mono.error(AuthenticationError.invalidEmail());
        }
        return userRepository.existsByEmail(normalized)
                .map(exists -> new ContinueResponse(exists ? "login" : "register"));
    }

    public Mono<AuthenticationResponse> login(String email, String password, ServerHttpResponse response) {
        String normalized = email.trim().toLowerCase();
        return userRepository.findByEmail(normalized)
                .switchIfEmpty(Mono.error(AuthenticationError.userNotFound()))
                .flatMap(user -> {
                    if (user.getPasswordHash() == null) {
                        return Mono.error(AuthenticationError.noPasswordSet());
                    }
                    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                        return Mono.error(AuthenticationError.invalidCredentials());
                    }
                    if (UserStatus.ACTIVE.name().equals(user.getStatus())) {
                        return issueTokens(user.getId(), user.getEmail(), response)
                                .thenReturn((AuthenticationResponse) new StepResponse("authenticated", null));
                    }
                    return onboardingService.findIncompleteSteps(user.getId())
                            .next()
                            .flatMap(step -> {
                                if (OnboardingStepType.OTP_VERIFICATION.name().equals(step.getStepName())) {
                                    // Send OTP and return pre-auth token so frontend can proceed
                                    return checkOtpRateLimit(user.getEmail())
                                            .then(otpService.invalidatePending(user.getId()))
                                            .then(otpService.create(user.getId(), user.getEmail()))
                                            .flatMap(code -> sendGridEmailService.sendOtp(user.getEmail(), code)
                                                    .thenReturn((AuthenticationResponse) new StepResponse("verify_otp",
                                                            createPreAuthToken(user.getId(), user.getEmail()))));
                                }
                                return issueTokens(user.getId(), user.getEmail(), response)
                                        .thenReturn((AuthenticationResponse) new StepResponse(
                                                step.getStepName(), null));
                            })
                            .switchIfEmpty(Mono.error(AuthenticationError.onboardingIncomplete("UNKNOWN")));
                });
    }

    private void clearAuthCookies(ServerHttpResponse response) {
        response.addCookie(
                ResponseCookie.from(cookieProperties.accessTokenCookieName(), "")
                        .path("/").maxAge(0).secure(true).httpOnly(true).build()
        );
        response.addCookie(
                ResponseCookie.from(cookieProperties.refreshTokenCookieName(), "")
                        .path("/").maxAge(0).secure(true).httpOnly(true).build()
        );
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String createPreAuthToken(UUID userId, String email) {
        return JWT.create()
                .withSubject(userId.toString())
                .withClaim("email", email)
                .withClaim("stage", "pre_auth")
                .withExpiresAt(Instant.now().plus(PRE_AUTH_TOKEN_EXPIRY))
                .sign(jwtSecret.getAlgorithm());
    }

    private Mono<Void> checkOtpRateLimit(String email) {
        Instant fiveMinutesAgo = Instant.now().minus(Duration.ofMinutes(5));
        return otpService.countRecentByEmail(email, fiveMinutesAgo)
                .flatMap(count -> {
                    if (count >= 3) {
                        return Mono.error(AuthenticationError.otpRateLimited());
                    }
                    return Mono.empty();
                });
    }

    private Mono<String> issueAccessToken(UUID userId, String email, ServerHttpResponse response) {
        String accessToken = JWT.create()
                .withSubject(userId.toString())
                .withClaim("email", email)
                .withClaim("type", "authenticated")
                .withExpiresAt(Instant.now().plus(ACCESS_TOKEN_EXPIRY))
                .sign(jwtSecret.getAlgorithm());

        String refreshTokenValue = UUID.randomUUID().toString();
        String refreshTokenHash = hashToken(refreshTokenValue);

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity()
                .setUserId(userId)
                .setTokenHash(refreshTokenHash)
                .setExpiresAt(Instant.now().plus(REFRESH_TOKEN_EXPIRY))
                .setCreatedAt(Instant.now());

        return refreshTokenRepository.save(refreshTokenEntity)
                .doOnSuccess(saved -> {
                    response.addCookie(buildCookie(cookieProperties.accessTokenCookieName(), accessToken, COOKIE_MAX_AGE_ACCESS));
                    response.addCookie(buildCookie(cookieProperties.refreshTokenCookieName(), refreshTokenValue, COOKIE_MAX_AGE_REFRESH));
                })
                .thenReturn(accessToken);
    }

    private ResponseCookie buildCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .path("/")
                .secure(true)
                .httpOnly(true)
                .maxAge(maxAge)
                .sameSite(Cookie.SameSite.LAX.attributeValue())
                .build();
    }
}
