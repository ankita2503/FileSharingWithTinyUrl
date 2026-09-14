package dev.system.tinyurl.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.util.Optional;

@Component
public class S3FileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;

    public S3FileStorage(S3Client s3, S3Presigner presigner, StorageProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
    }

    @Override
    public URI presignUpload(String objectKey, String contentType) {
        var put = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(props.uploadUrlTtl())
                .putObjectRequest(put)
                .build());
        return presigned.url().toString().transform(URI::create);
    }

    @Override
    public URI presignDownload(String objectKey, String filename) {
        var get = GetObjectRequest.builder()
                .bucket(props.bucket())
                .key(objectKey)
                .responseContentDisposition(
                        "attachment; filename=\"" + sanitizeFilename(filename) + "\"")
                .build();
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(props.downloadUrlTtl())
                .getObjectRequest(get)
                .build());
        return presigned.url().toString().transform(URI::create);
    }

    @Override
    public Optional<StoredObject> head(String objectKey) {
        try {
            var r = s3.headObject(HeadObjectRequest.builder()
                    .bucket(props.bucket()).key(objectKey).build());
            return Optional.of(new StoredObject(r.contentLength(), r.contentType(), r.lastModified()));
        } catch (S3Exception e) {
            if (e instanceof S3Exception s && s.statusCode() != 404) throw e;
            return Optional.empty();
        }
    }

    @Override
    public void delete(String objectKey) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(props.bucket()).key(objectKey).build());
        log.debug("Deleted object {}", objectKey);
    }

    /**
     * Content-Disposition is a response header built from user input: strip quotes,
     * CR/LF and path separators to prevent header injection and path confusion.
     */
    static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "download";
        String cleaned = filename.replaceAll("[\\r\\n\"\\\\/]", "_").trim();
        if (cleaned.length() > 200) cleaned = cleaned.substring(0, 200);
        return cleaned.isBlank() ? "download" : cleaned;
    }
}