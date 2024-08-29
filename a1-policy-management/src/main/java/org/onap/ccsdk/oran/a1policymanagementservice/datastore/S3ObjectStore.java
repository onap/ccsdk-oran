/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2022 Nordix Foundation. All rights reserved.
 * ======================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================LICENSE_END===================================
 */

package org.onap.ccsdk.oran.a1policymanagementservice.datastore;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

class S3ObjectStore implements DataStore {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final ApplicationConfig applicationConfig;

    private static S3AsyncClient s3AsynchClient;
    private final String location;

    public S3ObjectStore(ApplicationConfig applicationConfig, String location) {
        this.applicationConfig = applicationConfig;
        this.location = location;

        getS3AsynchClient(applicationConfig);
    }

    private static synchronized S3AsyncClient getS3AsynchClient(ApplicationConfig applicationConfig) {
        if (applicationConfig.isS3Enabled() && s3AsynchClient == null) {
            s3AsynchClient = getS3AsyncClientBuilder(applicationConfig).build();
        }
        return s3AsynchClient;
    }

    private static S3AsyncClientBuilder getS3AsyncClientBuilder(ApplicationConfig applicationConfig) {
        URI uri = URI.create(applicationConfig.getS3EndpointOverride());
        return S3AsyncClient.builder() //
                .region(Region.US_EAST_1) //
                .endpointOverride(uri) //
                .credentialsProvider(StaticCredentialsProvider.create( //
                        AwsBasicCredentials.create(applicationConfig.getS3AccessKeyId(), //
                                applicationConfig.getS3SecretAccessKey())));
    }

    @Override
    public Flux<String> listObjects(String prefix) {
        return listObjectsInBucket(bucket(), location + "/" + prefix).map(S3Object::key) //
                .map(this::externalName);
    }

    @Override
    public Mono<Boolean> deleteObject(String name) {
        DeleteObjectRequest request = DeleteObjectRequest.builder() //
                .bucket(bucket()) //
                .key(key(name)) //
                .build();

        CompletableFuture<DeleteObjectResponse> future = s3AsynchClient.deleteObject(request);

        return Mono.fromFuture(future).map(resp -> true);
    }

    @Override
    public Mono<byte[]> readObject(String name) {
        return getDataFromS3Object(bucket(), name);
    }

    @Override
    public Mono<byte[]> writeObject(String name, byte[] fileData) {

        PutObjectRequest request = PutObjectRequest.builder() //
                .bucket(bucket()) //
                .key(key(name)) //
                .build();

        AsyncRequestBody body = AsyncRequestBody.fromBytes(fileData);

        CompletableFuture<PutObjectResponse> future = s3AsynchClient.putObject(request, body);

        return Mono.fromFuture(future) //
                .map(putObjectResponse -> fileData) //
                .doOnError(t -> logger.error("Failed to store object '{}' in S3 {}", key(name), t.getMessage()));
    }

    @Override
    public Mono<String> createDataStore() {
        return createS3Bucket(bucket());
    }

    private Mono<String> createS3Bucket(String s3Bucket) {

        CreateBucketRequest request = CreateBucketRequest.builder() //
                .bucket(s3Bucket) //
                .build();

        CompletableFuture<CreateBucketResponse> future = s3AsynchClient.createBucket(request);

        return Mono.fromFuture(future) //
                .map(f -> s3Bucket) //
                .doOnError(t -> logger.debug("Could not create S3 bucket: {}", t.getMessage()))
                .onErrorResume(t -> Mono.just("Not Created"));
    }

    @Override
    public Mono<String> deleteAllObjects() {
        return listObjects("") //
                .flatMap(this::deleteObject) //
                .collectList() //
                .map(resp -> "OK").onErrorResume(t -> Mono.just("NOK"));
    }

    public Mono<String> deleteBucket() {
        DeleteBucketRequest request = DeleteBucketRequest.builder() //
                .bucket(bucket()) //
                .build();

        CompletableFuture<DeleteBucketResponse> future = s3AsynchClient.deleteBucket(request);

        return Mono.fromFuture(future) //
                .doOnError(t -> logger.warn("Could not delete bucket: {}, reason: {}", bucket(), t.getMessage()))
                .map(resp -> bucket()) //
                .doOnNext(resp -> logger.debug("Deleted bucket: {}", bucket())).onErrorResume(t -> Mono.just("NOK"));
    }

    private String bucket() {
        return applicationConfig.getS3Bucket();
    }

    private Flux<S3Object> listObjectsInBucket(String bucket, String prefix) {

        return listObjectsRequest(bucket, prefix, null) //
                .expand(response -> listObjectsRequest(bucket, prefix, response)) //
                .map(ListObjectsResponse::contents) //
                .doOnNext(f -> logger.debug("Found objects in {}: {}", bucket, f.size())) //
                .doOnError(t -> logger.warn("Error fromlist objects: {}", t.getMessage())) //
                .flatMap(Flux::fromIterable) //
                .doOnNext(obj -> logger.debug("Found object: {}", obj.key()));
    }

    private Mono<ListObjectsResponse> listObjectsRequest(String bucket, String prefix,
            ListObjectsResponse prevResponse) {
        ListObjectsRequest.Builder builder = ListObjectsRequest.builder() //
                .bucket(bucket) //
                .maxKeys(1000) //
                .prefix(prefix);

        if (prevResponse != null) {
            if (Boolean.TRUE.equals(prevResponse.isTruncated())) {
                builder.marker(prevResponse.nextMarker());
            } else {
                return Mono.empty();
            }
        }

        ListObjectsRequest listObjectsRequest = builder.build();
        CompletableFuture<ListObjectsResponse> future = s3AsynchClient.listObjects(listObjectsRequest);
        return Mono.fromFuture(future);
    }

    private Mono<byte[]> getDataFromS3Object(String bucket, String name) {

        GetObjectRequest request = GetObjectRequest.builder() //
                .bucket(bucket) //
                .key(key(name)) //
                .build();

        CompletableFuture<ResponseBytes<GetObjectResponse>> future =
                s3AsynchClient.getObject(request, AsyncResponseTransformer.toBytes());

        return Mono.fromFuture(future) //
                .map(BytesWrapper::asByteArray) //
                .doOnError(t -> logger.error("Failed to get file from S3, key:{}, bucket: {}, {}", key(name), bucket,
                        t.getMessage())) //
                .doOnEach(n -> logger.debug("Read file from S3: {} {}", bucket, key(name))) //
                .onErrorResume(t -> Mono.empty());
    }

    private String key(String name) {
        return location + "/" + name;
    }

    private String externalName(String internalName) {
        return internalName.substring(key("").length());
    }

}
