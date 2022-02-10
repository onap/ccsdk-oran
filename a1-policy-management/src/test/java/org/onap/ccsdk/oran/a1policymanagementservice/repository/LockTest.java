/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2020 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock.LockType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LockTest {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @SuppressWarnings("squid:S2925") // "Thread.sleep" should not be used in tests.
    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // Do nothing.
        }
    }

    private void asynchUnlock(Lock.Grant grant, Lock lock) {
        logger.info("Lock {} cnt: {}, exclusive: {}, queue: {}", grant.getLabel(), lock.getLockCounter(),
                lock.isExclusive, lock.lockRequestQueue.size());

        Thread thread = new Thread(() -> {
            sleep();
            grant.unlockBlocking();
        });
        thread.start();
    }

    @Test
    void testLock() throws IOException, ServiceException {
        Lock lock = new Lock("l1");
        Lock.Grant grant = lock.lockBlocking(LockType.SHARED, "test");
        grant.unlockBlocking();
        assertThat(grant.getLabel()).isEqualTo("test");

        grant = lock.lockBlocking(LockType.EXCLUSIVE, "");
        asynchUnlock(grant, lock);

        grant = lock.lockBlocking(LockType.SHARED, "");
        grant.unlockBlocking();

        assertThat(lock.getLockCounter()).isZero();
    }

    @Test
    void testReactiveLock() {
        Lock lock = new Lock("l1");

        Mono<?> l0 = lock.lock(LockType.EXCLUSIVE, "1").doOnNext(grant -> asynchUnlock(grant, lock));
        Mono<?> l1 = lock.lock(LockType.SHARED, "2").doOnNext(grant -> asynchUnlock(grant, lock));
        Mono<?> l2 = lock.lock(LockType.SHARED, "3").doOnNext(grant -> asynchUnlock(grant, lock));
        Mono<?> l3 = lock.lock(LockType.EXCLUSIVE, "4").doOnNext(grant -> asynchUnlock(grant, lock));
        Mono<?> l4 = lock.lock(LockType.SHARED, "5").doOnNext(grant -> asynchUnlock(grant, lock));

        StepVerifier.create(Flux.zip(l0, l1, l2, l3, l4)) //
                .expectSubscription() //
                .expectNextCount(1) //
                .verifyComplete();

        await().untilAsserted(() -> assertThat(lock.getLockCounter()).isZero());
    }
}
