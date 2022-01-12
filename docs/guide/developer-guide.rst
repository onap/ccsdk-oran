.. This work is licensed under a Creative Commons Attribution 4.0 International License.
.. http://creativecommons.org/licenses/by/4.0
.. Copyright (C) 2021 Nordix Foundation.

.. _developer_guide:

Developer Guide
===============

This document provides a quickstart for developers of the CCSDK functions for O-RAN A1 Policies.

Source tree
+++++++++++

This provides CCSDK with "A1 Policy Management Service" and "A1 Adapter" functions.
Each resource is implemented independently in a package corresponding to its name.

A1 Policy Management Service
++++++++++++++++++++++++++++

The ONAP CCSDK A1 Policy Management Service is a Java 11 web application built using the Spring Framework.
Using Spring Boot dependencies, it runs as a standalone application.

A1 Policy Management Service provides a REST API for management of policies. It provides support for:

 * Supervision of clients (R-APPs) to eliminate stray policies in case of failure
 * Consistency monitoring of the SMO view of policies and the actual situation in the RICs
 * Consistency monitoring of RIC capabilities (policy types)
 * Policy configuration. This includes:

   * One REST API towards all RICs in the network
   * Query functions that can find all policies in a RIC, all policies owned by a service (R-APP), all policies of a type etc.
   * Maps O1 resources (ManagedElement) as defined in O1 to the controlling RIC.

The Policy Management Service can be accessed over the REST API, and with an equivalent interface using DMaaP. See :ref:`pms_api` for more information about the API.

The configured A1 policies are stored presistently to survive a service restart. 

Dependencies
------------

This project uses various frameworks which are managed with Maven
dependency management tool (see *pom.xml* file at root level) :

To get a complete list of all dependencies, use command "mvn dependency:tree".

Configuration
-------------

There are two configuration files for A1 Policy Management Service, *config/application_configuration.json* and *config/application.yaml*
The first one contains configuration of data needed by the application, such as which Near-RT RICs
that are available. The second contains logging and security configurations.

For more information about these configuration files can be found as comments in the sample files provided with the source code, or on the `ONAP wiki <https://wiki.onap.org/display/DW/O-RAN+A1+Policies+in+ONAP+Honolulu>`_

Static configuration (application.yaml)
---------------------------------------

The file *./config/application.yaml* is read by the application at startup. It provides the following configurable features:

 * server; configuration for the WEB server

   * used port for HTTP/HTTPS, this is however not the port numbers visible outside the container
   * SSL parameters for setting up using of key store and trust store databases.
 * webclient; configuration parameters for a web client used by the component

   * SSL parameters for setting up using of key store and trust store databases.
   * Usage of HTTP(S) Proxy; if configured, the proxy will be used for southbound access to the NearRT-RICs

 * logging; setting of of which information that is logged.
 * filepath; the local path to a file used for dynamic configuration (if used). See next chapter.

For details about the parameters in this file, see documentation in the file.

Dynamic configuration
---------------------

The component has configuration that can be updated in runtime. This configuration is loaded from a file (accessible from the container). The configuration is re-read and refreshed at regular intervals. This file based configuration can be updated or read via the REST API, See :ref:`pms_api`.

The configuration includes:

 * Controller configuration, which includes information on how to access the a1-adapter
 * One entry for each NearRT-RIC, which includes:

   * The base URL of the NearRT RIC
   * A list of O1 identifiers that the NearRT RIC is controlling. An application can query this service which NearRT RIC should be addressed for controlling (for instance) a given Cell.
   * An optional reference to the controller to use, or excluded if the NearRT-RIC can be accessed directly from this component.

 * Optional configuration for using of DMAAP. There can be one stream for requests to the component and an other stream for responses.

For details about the syntax of the file, there is an example in source code repository */config/application_configuration.json*. This file is also included in the docker container */opt/app/policy-agent/data/application_configuration.json_example*.


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

   docker run -p 8081:8081 -p 8433:8433 --name=policy-agent-container --network=nonrtric-docker-net --volume "$PWD/new_keystore.jks:/opt/app/policy-agent/etc/cert/keystore.jks" --volume "$PWD/new_truststore.jks:/opt/app/policy-agent/etc/cert/truststore.jks" --volume "$PWD/new_application.yaml:/opt/app/policy-agent/config/application.yaml" onap/ccsdk-oran-a1policymanagementservice:1.2.0-SNAPSHOT

A1 Adapter (Internal)
+++++++++++++++++++++

The O-RAN A1 Adapter provides an **internal** RESTCONF API that is used by the A1 Policy Management System when accessing the A1 Interface. This API is useful for test and verification but should not used otherwise.

See :ref:`a1_adapter_api` for details of this internal API.

Configuration of HTTP Proxy
---------------------------

In order to configure a HTTP Proxy for southbound connections:
  * Modify file: odlsli/src/main/properties/a1-adapter-api-dg.properties in CCSDK/distribution
  * Variable a1Mediator.proxy.url must contain Proxy URL



