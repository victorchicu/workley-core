package ai.workley.core.auth.service;

import ai.workley.core.auth.model.OnboardingStepType;
import ai.workley.core.auth.model.UserStatus;
import ai.workley.core.auth.repository.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class SocialLoginService {
    private final OnboardingService onboardingService;
    private final R2dbcUserRepository userRepository;
    private final R2dbcUserProfileRepository userProfileRepository;
    private final R2dbcUserLinkedProviderRepository linkedProviderRepository;

    public SocialLoginService(
            OnboardingService onboardingService,
            R2dbcUserRepository userRepository,
            R2dbcUserProfileRepository userProfileRepository,
            R2dbcUserLinkedProviderRepository linkedProviderRepository
    ) {
        this.userRepository = userRepository;
        this.onboardingService = onboardingService;
        this.userProfileRepository = userProfileRepository;
        this.linkedProviderRepository = linkedProviderRepository;
    }

    public record SocialLoginResult(UUID userId, String email, String nextStep) {
    }

    public Mono<SocialLoginResult> authenticate(String provider, String subject, String email, boolean emailVerified, String fullName) {
        return linkedProviderRepository.findByProviderAndSubject(provider, subject)
                .flatMap(this::handleExistingLink)
                .switchIfEmpty(Mono.defer(() ->
                        emailVerified ? tryMergeOrCreate(provider, subject, email, fullName) : createNewSocialUser(provider, subject, email, fullName)
                ));
    }

    private String nextStepForUser(UserEntity user) {
        return UserStatus.ACTIVE.name().equals(user.getStatus())
                ? "authenticated"
                : "profile";
    }

    private Mono<Void> completeProfileIfPossible(UserEntity user, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return Mono.empty();
        }

        UserProfileEntity profile = new UserProfileEntity()
                .setUserId(user.getId())
                .setFullName(fullName.trim())
                .setAge(0)
                .setCreatedAt(Instant.now())
                .setUpdatedAt(Instant.now());

        return userProfileRepository.save(profile)
                .then();
    }

    private Mono<SocialLoginResult> handleExistingLink(UserLinkedProviderEntity linked) {
        return userRepository.findById(linked.getUserId())
                .map(user -> new SocialLoginResult(user.getId(), user.getEmail(), nextStepForUser(user)));
    }

    private Mono<SocialLoginResult> tryMergeOrCreate(String provider, String subject, String email, String fullName) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .flatMap(existingUser -> mergeWithExistingUser(existingUser, provider, subject, email))
                .switchIfEmpty(Mono.defer(() -> createNewSocialUser(provider, subject, email, fullName)));
    }

    private Mono<SocialLoginResult> mergeWithExistingUser(UserEntity existingUser, String provider, String subject, String email) {
        UserLinkedProviderEntity link = new UserLinkedProviderEntity()
                .setEmail(email)
                .setUserId(existingUser.getId())
                .setSubject(subject)
                .setProvider(provider)
                .setCreatedAt(Instant.now());
        return linkedProviderRepository.save(link)
                .thenReturn(
                        new SocialLoginResult(
                                existingUser.getId(), existingUser.getEmail(), nextStepForUser(existingUser))
                );
    }

    private Mono<SocialLoginResult> createNewSocialUser(String provider, String subject, String email, String fullName) {
        String normalizedEmail = email.trim().toLowerCase();
        UserEntity user = new UserEntity()
                .setEmail(normalizedEmail)
                .setStatus(UserStatus.CREATED.name())
                .setCreatedAt(Instant.now())
                .setUpdatedAt(Instant.now());

        return userRepository.save(user)
                .flatMap(savedUser -> {
                    UserLinkedProviderEntity link = new UserLinkedProviderEntity()
                            .setUserId(savedUser.getId())
                            .setEmail(normalizedEmail)
                            .setSubject(subject)
                            .setProvider(provider)
                            .setCreatedAt(Instant.now());
                    return linkedProviderRepository.save(link)
                            .thenReturn(savedUser);
                })
                .flatMap(savedUser -> onboardingService.initializeSteps(savedUser.getId())
                        .then(onboardingService.markStepCompleted(savedUser.getId(), OnboardingStepType.OTP_VERIFICATION))
                        .then(completeProfileIfPossible(savedUser, fullName))
                        .then(onboardingService.isFullyOnboarded(savedUser.getId()))
                        .flatMap(fullyOnboarded -> {
                            if (fullyOnboarded) {
                                savedUser.setStatus(UserStatus.ACTIVE.name());
                                savedUser.setUpdatedAt(Instant.now());
                                return userRepository.save(savedUser)
                                        .thenReturn(
                                                new SocialLoginResult(savedUser.getId(), savedUser.getEmail(), "authenticated"));
                            }
                            return Mono.just(
                                    new SocialLoginResult(
                                            savedUser.getId(), savedUser.getEmail(), "profile"));
                        })
                );
    }
}
