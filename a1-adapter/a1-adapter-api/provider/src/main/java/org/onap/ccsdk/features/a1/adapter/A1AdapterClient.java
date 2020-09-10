/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2020 Nordix Foundation.
 * ================================================================================
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * ============LICENSE_END=========================================================
 */

package org.onap.ccsdk.features.a1.adapter;

import java.util.Properties;
import org.onap.ccsdk.sli.core.sli.SvcLogicException;
import org.onap.ccsdk.sli.core.sli.provider.MdsalHelper;
import org.onap.ccsdk.sli.core.sli.provider.SvcLogicService;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.DeleteA1PolicyOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyOutputBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A1AdapterClient {

    private static final String PARAMETERS_PASSED_TO_SLI = "Parameters passed to SLI";
    private static final String PARAMETERS_RETURNED_BY_SLI = "Parameters returned by SLI";
    private static final String FAILURE = "failure";
    private static final String SVC_LOGIC_STATUS = "SvcLogic.status";

    private static final Logger LOG = LoggerFactory.getLogger(A1AdapterClient.class);

    private SvcLogicService svcLogicService = null;

    public A1AdapterClient(final SvcLogicService svcLogicService) {
        this.svcLogicService = svcLogicService;
    }

    public boolean hasGraph(String module, String rpc, String version, String mode) throws SvcLogicException {
        return svcLogicService.hasGraph(module, rpc, version, mode);
    }

    public Properties execute(String module, String rpc, String version, String mode,
            GetA1PolicyTypeOutputBuilder getPolicyTypeData, Properties parms) throws SvcLogicException {
        Properties localProp;
        localProp = MdsalHelper.toProperties(parms, getPolicyTypeData);
        if (LOG.isDebugEnabled()) {
            logParameters(PARAMETERS_PASSED_TO_SLI, localProp);
        }
        Properties respProps = svcLogicService.execute(module, rpc, version, mode, localProp);
        if (LOG.isDebugEnabled()) {
            logParameters(PARAMETERS_RETURNED_BY_SLI, localProp);
        }
        if (FAILURE.equalsIgnoreCase(respProps.getProperty(SVC_LOGIC_STATUS))) {
            return respProps;
        }
        MdsalHelper.toBuilder(respProps, getPolicyTypeData);
        return respProps;
    }

    public Properties execute(String module, String rpc, String version, String mode,
            GetA1PolicyStatusOutputBuilder getPolicyStatusData, Properties parms) throws SvcLogicException {
        Properties localProp;
        localProp = MdsalHelper.toProperties(parms, getPolicyStatusData);
        if (LOG.isDebugEnabled()) {
            logParameters(PARAMETERS_PASSED_TO_SLI, localProp);
        }
        Properties respProps = svcLogicService.execute(module, rpc, version, mode, localProp);
        if (LOG.isDebugEnabled()) {
            logParameters(PARAMETERS_RETURNED_BY_SLI, localProp);
        }
        if (FAILURE.equalsIgnoreCase(respProps.getProperty(SVC_LOGIC_STATUS))) {
            return respProps;
        }
        MdsalHelper.toBuilder(respProps, getPolicyStatusData);
        return respProps;
    }

    public Properties execute(String module, String rpc, String version, String mode,
            GetA1PolicyOutputBuilder getPolicyData, Properties parms) throws SvcLogicException {
        Properties localProp;
        localProp = MdsalHelper.toProperties(parms, getPolicyData);
        if (LOG.isDebugEnabled()) {
            logParameters(PARAMETERS_PASSED_TO_SLI, localProp);
        }
        Properties respProps = svcLogicService.execute(module, rpc, version, mode, localProp);
        if (LOG.isDebugEnabled()) {
            logParameters(PARAMETERS_RETURNED_BY_SLI, localProp);
        }
        if (FAILURE.equalsIgnoreCase(respProps.getProperty(SVC_LOGIC_STATUS))) {
            return respProps;
        }
        MdsalHelper.toBuilder(respProps, getPolicyData);
        return respProps;
    }

    public Properties execute(String module, String rpc, String version, String mode,
            DeleteA1PolicyOutputBuilder deletePolicyData, Properties parms) throws SvcLogicException {
        Properties localProp;
        localProp = MdsalHelper.toProperties(parms, deletePolicyData);
        if (LOG.isDebugEnabled()) {
            logParameters(PARAMETERS_PASSED_TO_SLI, localProp);
        }
        Properties respProps = svcLogicService.execute(module, rpc, version, mode, localProp);
        if (LOG.isDebugEnabled()) {
            logParameters(PARAMETERS_RETURNED_BY_SLI, localProp);
        }
        if (FAILURE.equalsIgnoreCase(respProps.getProperty(SVC_LOGIC_STATUS))) {
            return respProps;
        }
        MdsalHelper.toBuilder(respProps, deletePolicyData);
        return respProps;
    }

    public Properties execute(String module, String rpc, String version, String mode,
            PutA1PolicyOutputBuilder putPolicyData, Properties parms) throws SvcLogicException {
        Properties localProp;
        localProp = MdsalHelper.toProperties(parms, putPolicyData);
        if (LOG.isDebugEnabled()) {
            logParameters(PARAMETERS_PASSED_TO_SLI, localProp);
        }
        Properties respProps = svcLogicService.execute(module, rpc, version, mode, localProp);
        if (LOG.isDebugEnabled()) {
            logParameters(PARAMETERS_RETURNED_BY_SLI, localProp);
        }
        if (FAILURE.equalsIgnoreCase(respProps.getProperty(SVC_LOGIC_STATUS))) {
            return respProps;
        }
        MdsalHelper.toBuilder(respProps, putPolicyData);
        return respProps;
    }

    private void logParameters(String message, Properties localProp) {
        LOG.debug(message);

        for (Object key : localProp.keySet()) {
            String parmName = (String) key;
            String parmValue = localProp.getProperty(parmName);

            LOG.debug("{}={}", parmName, parmValue);
        }
    }
}
