# ONAP ccsdk-oran A1 Policy Management Service

The A1 Policy Management Service provides a REST API for management of policies.
It provides support for:
 -Supervision of clients (R-APPs) to eliminate stray policies in case of failure
 -Consistency monitoring of the SMO view of policies and the actual situation in the RICs
 -Consistency monitoring of RIC capabilities (policy types)
 -Policy configuration. This includes:
  -One REST API towards all RICs in the network
  -Query functions that can find all policies in a RIC, all policies owned by a service (R-APP),
   all policies of a type etc.
  -Maps O1 resources (ManagedElement) as defined in O1 to the controlling RIC

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
docker run -p 8081:8081 -p 8433:8433 --name=PMS-container --network=oran-docker-net --volume "$PWD/new_keystore.jks:/opt/app/policy-agent/etc/cert/keystore.jks" --volume "$PWD/new_truststore.jks:/opt/app/policy-agent/etc/cert/truststore.jks" --volume "$PWD/new_application.yaml:/opt/app/policy-agent/config/application.yaml" onap/ccsdk-oran-a1policymanagementservice:1.0.0-SNAPSHOT

To Run Policy Management Service in Local:
In the folder /opt/app/policy-agent/config/, create a soft link with below command,
ln -s <path to test_application_configuration.json> application_configuration.json

To Run Policy Management Service in Local with the DMaaP polling turned on:
In the folder /opt/app/policy-agent/config/, create a soft link with below command,
ln -s <path to test_application_configuration_with_dmaap_config.json> application_configuration.json

The Policy Management Service can be run stand alone in a simulated test mode. Then it simulates RICs.
The REST API is published on port 8081 and it is started by command:
mvn -Dtest=MockPolicyManagementService test

The backend server publishes live API documentation at the
URL `http://your-host-name-here:8081/swagger-ui.html`

The Policy Management Service uses A1-POLICY-AGENT-READ & A1-POLICY-AGENT-WRITE topic for subscribe & Publish to the DMaap.
Sample Request Message to DMaaP:
{
  "type": "request",
  "target": "policy-management-service",
  "timestamp": "2019-05-14T11:44:51.36Z",
  "operation": "GET",
  "correlationId": "c09ac7d1-de62-0016-2000-e63701125557-201",
  "apiVersion": "1.0",
  "originatorId": "849e6c6b420",
  "requestId": "23343221",
  "url": "/policies?type=type1&ric=ric1&service=service1"
}

Sample Response Message to DMaaP:
{
  "type": "response",
  "timestamp": "2019-05-14T11:44:51.36Z",
  "correlationId": "c09ac7d1-de62-0016-2000-e63701125557-201",
  "originatorId": "849e6c6b420",
  "requestId": "23343221",
  "status": "200 OK",
  "message": []
}

## License

ONAP : ccsdk oran
Copyright (C) 2019-2020 Nordix Foundation. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
