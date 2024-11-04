/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2020-2023 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.aspect;

import java.lang.invoke.MethodHandles;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Aspect
@Component
public class LogAspect {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Around("execution(* org.onap.ccsdk.oran.a1policymanagementservice.controllers..*(..))")
    public Object executimeTime(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Object result = proceedingJoinPoint.proceed();
        stopWatch.stop();
        MethodSignature methodSignature = (MethodSignature) proceedingJoinPoint.getSignature();
        String className = methodSignature.getDeclaringType().getSimpleName();
        String methodName = methodSignature.getName();
        logger.trace("Execution time of {}.{}: {} ms", className, methodName, stopWatch.getTotalTimeMillis());
        return result;
    }

    @Before("execution(* org.onap.ccsdk.oran.a1policymanagementservice.controllers..*(..))")
    public void entryLog(final JoinPoint joinPoint) {
        logger.trace("Entering method: {}", joinPoint.getSignature().getName());
    }

    @After("execution(* org.onap.ccsdk.oran.a1policymanagementservice.controllers..*(..))")
    public void exitLog(final JoinPoint joinPoint) {
        logger.trace("Exiting method: {}", joinPoint.getSignature().getName());
    }

}
