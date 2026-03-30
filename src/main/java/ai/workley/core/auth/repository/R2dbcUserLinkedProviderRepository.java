package ai.workley.core.auth.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface R2dbcUserLinkedProviderRepository extends ReactiveCrudRepository<UserLinkedProviderEntity, Long> {

    Mono<UserLinkedProviderEntity> findByProviderAndSubject(String provider, String subject);

    Flux<UserLinkedProviderEntity> findByUserId(UUID userId);
}
