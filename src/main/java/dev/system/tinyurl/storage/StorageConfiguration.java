package dev.system.tinyurl.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class StorageConfiguration {

    /** MinIO and R2 both require path-style addressing. */
    private static final S3Configuration PATH_STYLE =
            S3Configuration.builder().pathStyleAccessEnabled(true).build();

    @Bean
    AwsCredentialsProvider awsCredentialsProvider(StorageProperties props) {
        if (props.accessKey() != null && !props.accessKey().isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        }
        return DefaultCredentialsProvider.create();  // IAM role in a real cluster
    }

    @Bean
    S3Client s3Client(StorageProperties props, AwsCredentialsProvider creds) {
        var b = S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(creds)
                .serviceConfiguration(PATH_STYLE);
        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            b.endpointOverride(URI.create(props.endpoint()));
        }
        return b.build();
    }

    @Bean
    S3Presigner s3Presigner(StorageProperties props, AwsCredentialsProvider creds) {
        var b = S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(creds)
                .serviceConfiguration(PATH_STYLE);
        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            b.endpointOverride(URI.create(props.endpoint()));
        }
        return b.build();
    }
}