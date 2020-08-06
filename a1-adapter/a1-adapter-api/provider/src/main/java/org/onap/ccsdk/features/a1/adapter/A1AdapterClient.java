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
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeOutputBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A1AdapterClient {

  private static final Logger LOG = LoggerFactory.getLogger(A1AdapterClient.class);

  private SvcLogicService svcLogicService = null;

  public A1AdapterClient(final SvcLogicService svcLogicService) {
    this.svcLogicService = svcLogicService;
  }

  public boolean hasGraph(String module, String rpc, String version, String mode)
      throws SvcLogicException {
    return svcLogicService.hasGraph(module, rpc, version, mode);
  }

  public Properties execute(String module, String rpc, String version, String mode,
      GetA1PolicyTypeOutputBuilder serviceData, Properties parms) throws SvcLogicException {
    Properties localProp;
    localProp = MdsalHelper.toProperties(parms, serviceData);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Parameters passed to SLI");

      for (Object key : localProp.keySet()) {
        String parmName = (String) key;
        String parmValue = localProp.getProperty(parmName);

        LOG.debug(parmName + " = " + parmValue);
      }
    }
    Properties respProps = svcLogicService.execute(module, rpc, version, mode, localProp);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Parameters returned by SLI");
      for (Object key : respProps.keySet()) {
        String parmName = (String) key;
        String parmValue = respProps.getProperty(parmName);
        LOG.debug(parmName + " = " + parmValue);
      }
    }
    if ("failure".equalsIgnoreCase(respProps.getProperty("SvcLogic.status"))) {
      return respProps;
    }
    MdsalHelper.toBuilder(respProps, serviceData);
    return respProps;
  }
}
