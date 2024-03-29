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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import lombok.Getter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

/**
 * A resource lock. Exclusive means that the caller takes exclusive ownership of
 * the resurce. Non exclusive lock means that several users can lock the
 * resource (for shared usage).
 */
public class Lock {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    boolean isExclusive = false;
    private int lockCounter = 0;
    final Queue<LockRequest> lockRequestQueue = new LinkedList<>();
    private static AsynchCallbackExecutor callbackProcessor = new AsynchCallbackExecutor();
    private final String label;

    public enum LockType {
        EXCLUSIVE, SHARED
    }

    /**
     * A grant is achieved when the lock is granted.
     * It can be used for unlocking.
     */
    public static class Grant {
        private final Lock lock;
        private boolean unlocked = false;
        @Getter
        private final String label;

        Grant(Lock lock, String label) {
            this.lock = lock;
            this.label = label;
            logger.trace("Lock granted {}:{}", lock.label, this.label);
        }

        /**
         * reactive unlocking. Submits the lock.
         *
         * @return the lock
         */
        public Mono<Lock> unlock() {
            if (!isUnlocked()) {
                logger.trace("Unlocking lock {}:{}", lock.label, this.label);
                return this.lock.unlock();
            }
            return Mono.just(this.lock);
        }

        /**
         * Synchronuous unlocking
         */
        public void unlockBlocking() {
            if (!isUnlocked()) {
                logger.trace("Unlocking lock {}:{}", lock.label, this.label);
                this.lock.unlockBlocking();
            }
        }

        private boolean isUnlocked() {
            if (unlocked) {
                logger.debug("Lock {}:{} already unlocked", lock.label, this.label);
                return true;
            }
            unlocked = true;
            return false;
        }
    }

    /**
     *
     * @param label a label attached to the lock. For troubleshooting.
     */
    public Lock(String label) {
        this.label = label;
    }

    /**
     * Reactive lock. The Lock will be emitted when the lock is granted
     *
     * @param lockType type of lock (exclusive/shared)
     * @param label a label that will be attached to the request. Will be passed
     *        back in the Grant
     * @return a Grant that cane be used only to unlock.
     */
    public synchronized Mono<Grant> lock(LockType lockType, String label) {
        if (tryLock(lockType)) {
            return Mono.just(new Grant(this, label));
        } else {
            return Mono.create(monoSink -> addToQueue(monoSink, lockType, label));
        }
    }

    /**
     * A synchronuous variant of locking. The caller thread will be blocked util the
     * lock is granted.
     */
    public synchronized Grant lockBlocking(LockType locktype, String label) {
        while (!tryLock(locktype)) {
            this.waitForUnlock();
        }
        return new Grant(this, label);
    }

    public Mono<Lock> unlock() {
        return Mono.create(monoSink -> {
            unlockBlocking();
            monoSink.success(this);
        });
    }

    public synchronized void unlockBlocking() {
        if (lockCounter <= 0) {
            lockCounter = -1; // Might as well stop, to make it easier to find the problem
            logger.error("Number of unlocks must match the number of locks");
        }
        this.lockCounter--;
        if (lockCounter == 0) {
            isExclusive = false;
        }
        this.notifyAll();
        this.processQueuedEntries();
    }

    @Override
    public synchronized String toString() {
        return "Lock " + this.label + ", cnt: " + this.lockCounter + ", exclusive: " + this.isExclusive + ", queued: "
                + this.lockRequestQueue.size();
    }

    /** returns the current number of granted locks */
    public synchronized int getLockCounter() {
        return this.lockCounter;
    }

    private void processQueuedEntries() {
        List<LockRequest> granted = new ArrayList<>();
        while (!lockRequestQueue.isEmpty()) {
            LockRequest request = lockRequestQueue.element();
            if (tryLock(request.lockType)) {
                lockRequestQueue.remove();
                granted.add(request);
            } else {
                break; // Avoid starvation
            }
        }
        callbackProcessor.addAll(granted);
    }

    private synchronized void addToQueue(MonoSink<Grant> callback, LockType lockType, String label) {
        logger.trace("Lock request queued {}:{}", label, this.label);
        lockRequestQueue.add(new LockRequest(callback, lockType, this, label));
    }

    @SuppressWarnings("java:S2274") // Always invoke wait() and await() methods inside a loop
    private synchronized void waitForUnlock() {
        try {
            this.wait();
        } catch (InterruptedException e) {
            logger.warn("waitForUnlock interrupted " + this.label, e);
            Thread.currentThread().interrupt();
        }
    }

    private boolean tryLock(LockType lockType) {
        if (this.isExclusive) {
            return false;
        }
        if (lockType == LockType.EXCLUSIVE && lockCounter > 0) {
            return false;
        }
        lockCounter++;
        this.isExclusive = lockType == LockType.EXCLUSIVE;
        return true;
    }

    /**
     * Represents a queued lock request
     */
    private static class LockRequest {
        final MonoSink<Grant> callback;
        final LockType lockType;
        final Lock lock;
        final String label;

        LockRequest(MonoSink<Grant> callback, LockType lockType, Lock lock, String label) {
            this.callback = callback;
            this.lockType = lockType;
            this.lock = lock;
            this.label = label;
        }
    }

    /**
     * A separate thread that calls a MonoSink to continue. This is done after a
     * queued lock is granted.
     */
    private static class AsynchCallbackExecutor implements Runnable {
        private List<LockRequest> lockRequestQueue = new LinkedList<>();

        public AsynchCallbackExecutor() {
            Thread thread = new Thread(this);
            thread.start();
        }

        public synchronized void addAll(List<LockRequest> requests) {
            this.lockRequestQueue.addAll(requests);
            this.notifyAll();
        }

        @Override
        public void run() {
            try {
                while (true) {
                    for (LockRequest request : consume()) {
                        Grant g = new Grant(request.lock, request.label);
                        request.callback.success(g);
                    }
                    waitForNewEntries();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted {}", e.getMessage());
            }
        }

        private synchronized List<LockRequest> consume() {
            List<LockRequest> q = this.lockRequestQueue;
            this.lockRequestQueue = new LinkedList<>();
            return q;
        }

        @SuppressWarnings("java:S2274")
        private synchronized void waitForNewEntries() throws InterruptedException {
            if (this.lockRequestQueue.isEmpty()) {
                this.wait();
            }
        }
    }
}
