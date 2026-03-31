package ai.workley.core.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;

@Service
public class S3StorageService {
    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3AsyncClient s3Client;
    private final String bucket;

    public S3StorageService(S3AsyncClient s3Client, @Value("${storage.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @PostConstruct
    void ensureBucketExists() {
        s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build())
                .exceptionally(error -> {
                    log.info("Bucket '{}' not found, creating...", bucket);
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build()).join();
                    return null;
                }).join();
    }

    public Mono<Void> putObject(String key, byte[] data, String contentType) {
        return Mono.fromFuture(() ->
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentType(contentType)
                                .contentLength((long) data.length)
                                .build(),
                        AsyncRequestBody.fromBytes(data)
                )
        ).then();
    }

    public Mono<byte[]> getObject(String key) {
        return Mono.fromFuture(() ->
                s3Client.getObject(
                        GetObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .build(),
                        AsyncResponseTransformer.toBytes()
                )
        ).map(response -> response.asByteArray());
    }

    public Mono<Void> deleteObject(String key) {
        return Mono.fromFuture(() ->
                s3Client.deleteObject(
                        DeleteObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .build()
                )
        ).then();
    }
}
