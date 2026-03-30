package ai.workley.core.auth.model;

import org.springframework.http.HttpStatus;

public class AuthenticationError extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    public AuthenticationError(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static AuthenticationError invalidEmail() {
        return new AuthenticationError("invalid_email", "Email format is invalid", HttpStatus.BAD_REQUEST);
    }

    public static AuthenticationError passwordTooShort() {
        return new AuthenticationError("password_too_short", "Password must be at least 8 characters", HttpStatus.BAD_REQUEST);
    }

    public static AuthenticationError passwordsMismatch() {
        return new AuthenticationError("passwords_mismatch", "Passwords don't match", HttpStatus.BAD_REQUEST);
    }

    public static AuthenticationError invalidCredentials() {
        return new AuthenticationError("invalid_credentials", "Wrong password", HttpStatus.UNAUTHORIZED);
    }

    public static AuthenticationError invalidOtp() {
        return new AuthenticationError("invalid_otp", "Invalid verification code", HttpStatus.UNAUTHORIZED);
    }

    public static AuthenticationError invalidPreAuth() {
        return new AuthenticationError("invalid_pre_auth", "Session expired, please start over", HttpStatus.UNAUTHORIZED);
    }

    public static AuthenticationError emailAlreadyExists() {
        return new AuthenticationError("email_exists", "Email already registered", HttpStatus.CONFLICT);
    }

    public static AuthenticationError userNotFound() {
        return new AuthenticationError("user_not_found", "User not found", HttpStatus.UNAUTHORIZED);
    }

    public static AuthenticationError otpExpired() {
        return new AuthenticationError("otp_expired", "Verification code has expired", HttpStatus.UNAUTHORIZED);
    }

    public static AuthenticationError otpMaxAttemptsExceeded() {
        return new AuthenticationError("otp_max_attempts", "Too many incorrect attempts", HttpStatus.UNAUTHORIZED);
    }

    public static AuthenticationError otpRateLimited() {
        return new AuthenticationError("otp_rate_limited", "Too many requests, please wait before requesting a new code", HttpStatus.TOO_MANY_REQUESTS);
    }

    public static AuthenticationError emailDeliveryFailed() {
        return new AuthenticationError("email_delivery_failed", "Something went wrong while sending the verification email, please try again later", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static AuthenticationError invalidFullName() {
        return new AuthenticationError("invalid_full_name", "Full name is required", HttpStatus.BAD_REQUEST);
    }

    public static AuthenticationError underage() {
        return new AuthenticationError("underage", "You must be at least 18 years old", HttpStatus.FORBIDDEN);
    }

    public static AuthenticationError profileAlreadyCompleted() {
        return new AuthenticationError("profile_already_completed", "Profile already completed", HttpStatus.CONFLICT);
    }

    public static AuthenticationError noPasswordSet() {
        return new AuthenticationError("no_password_set",
                "No password set. Please log in with Google or set a password.", HttpStatus.UNAUTHORIZED);
    }

    public static AuthenticationError onboardingIncomplete(String nextStep) {
        return new AuthenticationError("onboarding_incomplete", nextStep, HttpStatus.FORBIDDEN);
    }
}
