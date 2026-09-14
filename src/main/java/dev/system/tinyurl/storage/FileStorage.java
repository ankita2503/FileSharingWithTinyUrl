package dev.system.tinyurl.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/** Storage abstraction. Bytes never pass through the application. */
public interface FileStorage {

    record StoredObject(long sizeBytes, String contentType, Instant lastModified) {}

    /** Presigned PUT the browser uploads to directly. */
    URI presignUpload(String objectKey, String contentType);

    /** Short-lived presigned GET; filename drives Content-Disposition. */
    URI presignDownload(String objectKey, String filename);

    /** Present only if the object actually landed. */
    Optional<StoredObject> head(String objectKey);

    void delete(String objectKey);
}