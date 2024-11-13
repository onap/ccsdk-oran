package org.onap.ccsdk.oran.a1policymanagementservice.util.v3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.lang.invoke.MethodHandles;

public class ReactiveEntryExitFilterCondition implements Condition {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String reactiveFilterEnabled = context.getEnvironment().getProperty("logging.reactive-entry-exit-filter-enabled", "false");
        logger.info("Reactive Entry Exit filter is enabled: {}", reactiveFilterEnabled);
        return Boolean.parseBoolean(reactiveFilterEnabled);
    }
}
