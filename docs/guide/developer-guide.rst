.. This work is licensed under a Creative Commons Attribution 4.0 International License.
.. http://creativecommons.org/licenses/by/4.0
.. Copyright (C) 2020 Nordix Foundation.

Developer Guide
===============

This document provides a quickstart for developers of the CCSDK ORAN parts.

A1 Adapter
++++++++++

TBD

The A1 Adapter can be accessed over the REST API. See :ref:`a1-adapter-api` for how to use the API.


A1 Policy Management
++++++++++++++++++++

The CCSDK Policy Management Service (PMS) provides a REST API for management of policices. It provides support for:

 * Supervision of clients (R-APPs) to eliminate stray policies in case of failure
 * Consistency monitoring of the SMO view of policies and the actual situation in the RICs
 * Consistency monitoring of RIC capabilities (policy types)
 * Policy configuration. This includes:

   * One REST API towards all RICs in the network
   * Query functions that can find all policies in a RIC, all policies owned by a service (R-APP), all policies of a type etc.
   * Maps O1 resources (ManagedElement) as defined in O1 to the controlling RIC.

The Policy Management Service can be accessed over the REST API. See :ref:`pms-api` for how to use the API.

Configuration of certs
----------------------
The Policy Management Service uses the default keystore and truststore that are built into the container. The paths and passwords for these stores are located in a yaml file:
 oran/a1-policy-management/config/application.yaml

There is also Policy Management Service's own cert in the default truststore for mocking purposes and unit-testing (ApplicationTest.java).

The default keystore, truststore, and application.yaml files can be overridden by mounting new files using the "volumes" field of docker-compose or docker run command.

Assuming that the keystore, truststore, and application.yaml files are located in the same directory as docker-compose, the volumes field should have these entries:

`volumes:`
      `- ./new_keystore.jks:/opt/app/policy-agent/etc/cert/keystore.jks:ro`

      `- ./new_truststore.jks:/opt/app/policy-agent/etc/cert/truststore.jks:ro`

      `- ./new_application.yaml:/opt/app/policy-agent/config/application.yaml:ro`

The target paths in the container should not be modified.

Example docker run command for mounting new files (assuming they are located in the current directory):

`docker run -p 8081:8081 -p 8433:8433 --name=policy-agent-container --network=nonrtric-docker-net --volume "$PWD/new_keystore.jks:/opt/app/policy-agent/etc/cert/keystore.jks" --volume "$PWD/new_truststore.jks:/opt/app/policy-agent/etc/cert/truststore.jks" --volume "$PWD/new_application.yaml:/opt/app/policy-agent/config/application.yaml" o-ran-sc/nonrtric-policy-agent:2.1.0-SNAPSHOT`
