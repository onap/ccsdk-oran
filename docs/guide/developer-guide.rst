.. This work is licensed under a Creative Commons Attribution 4.0 International License.
.. http://creativecommons.org/licenses/by/4.0
.. Copyright (C) 2020 Nordix Foundation.

.. _developer_guide:

Developer Guide
===============

This document provides a quickstart for developers of the CCSDK ORAN parts.

Source tree
+++++++++++

This application provides CCSDK Policy Management Service and A1 Adapter as main functional resources.
Each resource is implemented independently in a package corresponding to its name.

A1 Policy Management Service
++++++++++++++++++++++++++++

The CCSDK Policy Management Service (PMS) is a Java 11 web application built over Spring Framework.
Using Spring Boot dependencies, it runs as a standalone application.

PMS provides a REST API for management of policices. It provides support for:

 * Supervision of clients (R-APPs) to eliminate stray policies in case of failure
 * Consistency monitoring of the SMO view of policies and the actual situation in the RICs
 * Consistency monitoring of RIC capabilities (policy types)
 * Policy configuration. This includes:

   * One REST API towards all RICs in the network
   * Query functions that can find all policies in a RIC, all policies owned by a service (R-APP), all policies of a type etc.
   * Maps O1 resources (ManagedElement) as defined in O1 to the controlling RIC.

The Policy Management Service can be accessed over the REST API. See :ref:`pms_api` for how to use the API.

Dependencies
------------

This project uses various frameworks which are managed with Maven
dependency management tool (see *pom.xml* file at root level) :

- Swagger annotations
- `Spring Framework <https://github.com/spring-projects/spring-boot>`_
- `Springfox <https://github.com/springfox/springfox>`_ Automated JSON API documentation for API's built with Spring
- `Immutable <https://immutables.github.io/>`_ to generate simple, safe and consistent value objects
- `JSON in Java <https://github.com/stleary/JSON-java>`_ to parse JSON documents into Java objects
- `Apache Commons Net <https://github.com/apache/commons-net>`_ for network utilities and protocol implementations
- `DCAE SDK <https://github.com/onap/dcaegen2-services-sdk>`_ to get configuration from CBS
- `Lombok <https://github.com/rzwitserloot/lombok>`_ to generate code, such as getters and setters
- `Awaitility <https://github.com/awaitility/awaitility>`_ to test asynchronous functionality

Configuration
-------------

There are two configuration files for PMS, *config/application_configuration.json* and *config/application.yaml*.
The first one contains configuration of data needed by the application, such as which Near-RT RICs
that are available. The second contains logging and security configurations.

Configuration of certs
----------------------

The Policy Management Service uses the default keystore and truststore that are built into the container. The paths and
passwords for these stores are located in a yaml file: ::

   oran/a1-policy-management/config/application.yaml

There is also Policy Management Service's own cert in the default truststore for mocking purposes and unit-testing
(ApplicationTest.java).

The default keystore, truststore, and application.yaml files can be overridden by mounting new files using the "volumes"
field of docker-compose or docker run command.

Assuming that the keystore, truststore, and application.yaml files are located in the same directory as docker-compose,
the volumes field should have these entries: ::

   `volumes:`
      `- ./new_keystore.jks:/opt/app/policy-agent/etc/cert/keystore.jks:ro`

      `- ./new_truststore.jks:/opt/app/policy-agent/etc/cert/truststore.jks:ro`

      `- ./new_application.yaml:/opt/app/policy-agent/config/application.yaml:ro`

The target paths in the container should not be modified.

Example docker run command for mounting new files (assuming they are located in the current directory): ::

   docker run -p 8081:8081 -p 8433:8433 --name=PMS-container --network=oran-docker-net --volume "$PWD/new_keystore.jks:/opt/app/policy-agent/etc/cert/keystore.jks" --volume "$PWD/new_truststore.jks:/opt/app/policy-agent/etc/cert/truststore.jks" --volume "$PWD/new_application.yaml:/opt/app/policy-agent/config/application.yaml" onap/ccsdk-oran-a1policymanagementservice:1.1.2-SNAPSHOT

A1 Adapter (Internal)
+++++++++++++++++++++

The O-RAN A1 Adapter provides an internal REST CONF API for management of A1 policices, useful for test and verification.

The A1 Adapter can be accessed over the REST CONF API. See :ref:`a1_adapter_api` for how to use the API.
