package com.clubhub.file;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.util.Optional;

/**
 * The only class that talks to S3. Pre-signing is a local HMAC computation (no network call);
 * only {@link #head} actually reaches S3.
 */
@Component
public class FileStorage {

    public record ObjectInfo(long sizeBytes, String contentType) {
    }

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;

    public FileStorage(S3Client s3, S3Presigner presigner, StorageProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
    }

    /**
     * Content-Type and Content-Length are part of the signature: the client must upload exactly the
     * type and size it declared, so it can't swap a 5 MB PNG for a 2 GB video with the same URL.
     */
    public URI presignUpload(String key, String contentType, long sizeBytes) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(props.bucket()).key(key).contentType(contentType).contentLength(sizeBytes).build();
        return URI.create(presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(props.uploadUrlTtl()).putObjectRequest(put).build()).url().toString());
    }

    public URI presignDownload(String key) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(props.bucket()).key(key).build();
        return URI.create(presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(props.downloadUrlTtl()).getObjectRequest(get).build()).url().toString());
    }

    /** What actually landed in the bucket, or empty if nothing was uploaded under this key. */
    public Optional<ObjectInfo> head(String key) {
        try {
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder().bucket(props.bucket()).key(key).build());
            return Optional.of(new ObjectInfo(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }
}
