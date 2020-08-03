#
# ============LICENSE_START=======================================================
# ONAP : ccsdk oran
# ================================================================================
# Copyright (C) 2019 Nordix Foundation. All rights reserved.
# ================================================================================
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# ============LICENSE_END=========================================================
#
FROM openjdk:11-jre-slim

EXPOSE 8081 8433

ARG JAR

WORKDIR /opt/app/policy-agent
RUN mkdir -p /var/log/policy-agent
RUN mkdir -p /opt/app/policy-agent/etc/cert/

RUN groupadd -r onap && useradd -ms /bin/bash a1pms -g onap
RUN chown -R a1pms:onap /var/log/policy-agent

ADD /config/application.yaml /opt/app/policy-agent/config/application.yaml
ADD /config/application_configuration.json /opt/app/policy-agent/data/application_configuration.json_example
ADD /config/keystore.jks /opt/app/policy-agent/etc/cert/keystore.jks
ADD /config/truststore.jks /opt/app/policy-agent/etc/cert/truststore.jks

RUN chmod -R 777 /opt/app/policy-agent/config/

USER a1pms

ADD target/${JAR} /opt/app/policy-agent/a1-policy-management-service.jar

CMD ["java", "-jar", "/opt/app/policy-agent/a1-policy-management-service.jar"]
