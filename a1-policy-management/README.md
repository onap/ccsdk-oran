# ONAP ccsdk-oran A1 Policy Management Service

The A1 Policy Management Service is a micro service which maintains a transient repository of:
  - All A1 policies instances in the network. Each policy is targeted to a near-RT-RIC instance and is owned by a 'service' (or 'rApp').
  - All near-RT-RICs in the network.
  - All Policy types supported by each near-RT-RIC.

The service provides :
  - Unified REST API for managing A1 Policies in all near-RT-RICs.
  - Synchronized view of A1 Policy instances for each rApp/Client
  - Synchronized view of A1 Policy instances in each near-RT-RIC
  - Synchronized view of A1 Policy types supported by each near-RT-RIC
  - Lookup service to find the near-RT-RIC to control resources in the RAN as defined in  O1 (e.g. which near-RT-RIC should be accessed to control a certain CU or DU, which in turn controls a certain cell).
  - Monitors all near-RT-RICs and maintains data consistency  – e.g. recovery from near-RT-RIC restarts
  - Support for different Southbound APIs to the near-RT-RICs (different versions of the A1-P application protocol and other similar APIs).
  - HTTPS can be configured to use a supplied certificate/private key and to validate peers towards a list of trusted CAs/certs.
  - HTTP proxy support for tunneling HTTP/HTTPS connections.
  - Fine-grained access-control - with new optional callouts to an external auth function

The Policy Management Service uses the default keystore and truststore that are built into the container. The paths and passwords for these stores are located in a yaml file:
oran/a1-policy-management/config/application.yaml

There is also Policy Management Service's own cert in the default truststore for mocking purposes and unit-testing (ApplicationTest.java).

The default keystore, truststore, and application.yaml files can be overridden by mounting new files using the "volumes" field of docker-compose or docker run command.

Assuming that the keystore, truststore, and application.yaml files are located in the same directory as docker-compose, the volumes field should have these entries:

volumes:
  - ./new_keystore.jks:/opt/app/policy-agent/etc/cert/keystore.jks:ro
  - ./new_truststore.jks:/opt/app/policy-agent/etc/cert/truststore.jks:ro
  - ./new_application.yaml:/opt/app/policy-agent/config/application.yaml:ro

The target paths in the container should not be modified.

It is also possible to configure a HTTP(S) Proxy for southbound connections. This can be set in the application.yaml configuration file.

Example docker run command for mounting new files (assuming they are located in the current directory):
docker run -p 8081:8081 -p 8433:8433 --name=PMS-container --network=oran-docker-net --volume "$PWD/new_keystore.jks:/opt/app/policy-agent/etc/cert/keystore.jks" --volume "$PWD/new_truststore.jks:/opt/app/policy-agent/etc/cert/truststore.jks" --volume "$PWD/new_application.yaml:/opt/app/policy-agent/config/application.yaml" onap/ccsdk-oran-a1policymanagementservice:2.0.1-SNAPSHOT

To run A1 Policy Management Service in a local environment:
In the folder /opt/app/policy-agent/config/, create a soft link with below command,
ln -s <path to test_application_configuration.json> application_configuration.json

The A1 Policy Management Service can be run stand alone in a simulated test mode. Then it simulates RICs.
The REST API is published on port 8081 and it is started by command:
mvn -Dtest=MockPolicyManagementService test

The backend server publishes live API documentation at the
URL `http://your-host-name-here:8081/swagger-ui.html`

More information about the ONAP CCSDK A1 Policy Management Service can be found at: 
  - https://lf-onap.atlassian.net/wiki/spaces/DW/pages/16444961/O-RAN+A1+Policies+in+ONAP
  - https://docs.onap.org/projects/onap-ccsdk-oran/en/latest/index.html


## License

ONAP : ccsdk oran
Copyright (C) 2019-2023 Nordix Foundation. All rights reserved.
Copyright (C) 2023-2025 OpenInfra Foundation Europe. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
