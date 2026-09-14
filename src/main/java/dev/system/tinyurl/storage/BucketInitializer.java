package dev.system.tinyurl.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** Creates the bucket on first run so a clean checkout works with no manual setup. */
@Component
public class BucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BucketInitializer.class);

    private final S3Client s3;
    private final StorageProperties props;

    public BucketInitializer(S3Client s3, StorageProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
            log.info("Using existing bucket {}", props.bucket());
        } catch (S3Exception e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
            log.info("Created bucket {}", props.bucket());
        }
    }
}