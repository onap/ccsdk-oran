package org.onap.ccsdk.oran.a1policymanagementservice.datastore;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.reactivestreams.Publisher;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S3ObjectStoreTest {

    static ApplicationConfig appConfig;
    private static S3ObjectStore s3ObjectStore;
    private static final String bucketName = "s3bucket";

    @Container
    private static final LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:0.11.3"))
                    .withServices(LocalStackContainer.Service.S3);

    @BeforeAll
    static void init() {
        appConfig = mock(ApplicationConfig.class);
        when(appConfig.isS3Enabled()).thenReturn(Boolean.TRUE);
        when(appConfig.getS3EndpointOverride()).thenReturn(localstack.getEndpoint().toString());
        when(appConfig.getS3AccessKeyId()).thenReturn(localstack.getAccessKey());
        when(appConfig.getS3SecretAccessKey()).thenReturn(localstack.getSecretKey());
        when(appConfig.getS3Bucket()).thenReturn(bucketName);
        s3ObjectStore = new S3ObjectStore(appConfig, "location");
    }
    @Test
    @Order(1)
    void testGetS3AsynchClient() {
        assertNotNull(s3ObjectStore);
    }

    @Test
    @Order(2)
    void testCreateAndDeleteS3BucketSuccess(CapturedOutput capturedOutput) {

        testSuccess(s3ObjectStore.createDataStore(), actual -> actual.equals(bucketName));
        assertFalse(capturedOutput.getOut().contains("Could not create S3 bucket:"));

        testSuccess(s3ObjectStore.deleteBucket(), actual -> actual.equals(bucketName));
    }

    @Test
    @Order(3)
    void testWriteAndReadAndDeleteObjectSuccess(CapturedOutput capturedOutput) {

        testSuccess(s3ObjectStore.createDataStore(), actual -> actual.equals(bucketName));
        byte[] fileData = "testData".getBytes(StandardCharsets.UTF_8);
        new String(fileData);
        testSuccess(s3ObjectStore.writeObject("test", fileData),
                actual -> Arrays.equals(actual, fileData));
        assertFalse(capturedOutput.getOut().contains("Failed to store object"));
        testSuccess(s3ObjectStore.readObject("test"),
                actual -> Arrays.equals(actual, fileData));
        testSuccess(s3ObjectStore.deleteAllObjects(), actual -> actual.equals("OK"));
    }

    @Test
    @Order(4)
    void testListObjectsSuccess() {

        s3ObjectStore.createDataStore().block();
        String objectName = "test";
        byte[] fileData = "testData".getBytes(StandardCharsets.UTF_8);
        testSuccess(s3ObjectStore.writeObject(objectName, fileData),
                actual -> Arrays.equals(actual, fileData));
        testSuccess(s3ObjectStore.listObjects(""), actual -> actual.equals(objectName));
    }

    @Test
    @Order(5)
    void testCreateAndDeleteS3BucketError(CapturedOutput capturedOutput) {

        when(appConfig.getS3Bucket()).thenReturn("S3Bucket");

        testFailure(s3ObjectStore.createDataStore(), actual -> actual.equals("Not Created"));

        testFailure(s3ObjectStore.deleteBucket(), actual -> actual.equals("NOK"));
        assertTrue(capturedOutput.getOut().contains("Could not delete bucket:"));
    }

    <T> void testSuccess(Publisher<T> publisher, Predicate<T> equalityCheck) {
        StepVerifier.create(publisher)
                .expectNextMatches(equalityCheck)
                .verifyComplete();
    }

    <T> void testFailure(Publisher<T> publisher, Predicate<T> equalityCheck) {
        StepVerifier.create(publisher)
                .expectNextMatches(equalityCheck)
                .verifyComplete();
    }
}
