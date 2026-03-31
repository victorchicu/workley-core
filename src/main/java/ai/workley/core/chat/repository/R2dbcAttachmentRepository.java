package ai.workley.core.chat.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface R2dbcAttachmentRepository extends ReactiveCrudRepository<AttachmentEntity, UUID> {

    @Modifying
    @Query("UPDATE attachments SET expires_at = NULL WHERE id = :id AND uploaded_by = :uploadedBy AND expires_at IS NOT NULL")
    Mono<Integer> linkAttachment(UUID id, UUID uploadedBy);

    @Query("SELECT * FROM attachments WHERE expires_at < NOW()")
    Flux<AttachmentEntity> findOrphans();

    @Query("SELECT * FROM attachments WHERE id = :id AND uploaded_by = :uploadedBy AND expires_at IS NOT NULL")
    Mono<AttachmentEntity> findUnlinkedByIdAndOwner(UUID id, UUID uploadedBy);
}
