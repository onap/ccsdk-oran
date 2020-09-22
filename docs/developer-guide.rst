.. This work is licensed under a Creative Commons Attribution 4.0 International License.
.. SPDX-License-Identifier: CC-BY-4.0
.. Copyright (C) 2020 Nordix

Developer Guide
===============

This document provides a quickstart for developers of the CCSDK ORAN parts.

A1 Adapter
++++++++++

Prerequisites
-------------

1. Java development kit (JDK), version 11
2. Maven dependency-management tool, version 3.6 or later
3. Python, version 3
4. Docker, version 19.03.1 or latest
5. Docker Compose, version 1.24.1 or latest

Build and run
-------------
The following repos must be cloned, apart from the oran repo; ::
    "ccsdk/distribution"
    "ccsdk/oam"
    the simulator repo from O-RAN-SC, "sim/a1-interface".

Go to the "ccsdk-oran/a1-adapter" folder and build the A1 adapter with the following command ::

    oran/a1-adapter mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true

Go to the "ccsdk-distribution" repo and build with the following command ::

    distribution mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true -Pdocker

Go to the "ccsdk-oam" repo and build with the following command ::

    oam mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true -Pdocker

To see that the images are built, run the commands below and make sure that the "onap/ccsdk-odlsli-alpine-image" and
"onap/sdnc-image" images are built within the time frame of your recent build. ::

    docker images | grep ccsdk
    docker images | grep sdnc

Go the "installation/src/main/yaml" folder in the oam repo.
Edit the "docker-compose.yml" file so that it has the following content ::

    version: '2.1'

    networks:
      default:
        driver: bridge

    services:
      db:
        image: mysql/mysql-server:5.6
        container_name: sdnc_db_container
        ports:
          - "3306"
        environment:
          - MYSQL_ROOT_PASSWORD=openECOMP1.0
          - MYSQL_ROOT_HOST=%
        logging:
          driver:   "json-file"
          options:
            max-size: "30m"
            max-file: "5"

      sdnc:
        image: 'nexus3.onap.org:10003/onap/sdnc-image:2.0.1-STAGING-latest'
        #image: 'onap/sdnc-image:latest'
        depends_on :
          - db
        container_name: sdnc_controller_container
        entrypoint: ["/opt/onap/sdnc/bin/startODL.sh"]
        ports:
          - "8282:8181"
        links:
          - db:dbhost
          - db:sdnctldb01
          - db:sdnctldb02
        environment:
          - MYSQL_ROOT_PASSWORD=openECOMP1.0
          - SDNC_CONFIG_DIR=/opt/onap/sdnc/data/properties
          - SDNC_BIN=/opt/onap/sdnc/bin
          - ODL_CERT_DIR=/tmp
          - ODL_ADMIN_USERNAME=admin
          - ODL_ADMIN_PASSWORD=Kp8bJ4SXszM0WXlhak3eHlcse2gAw84vaoGGmJvUy2U
          - A1_TRUSTSTORE_PASSWORD=a1adapter
        # The default truststore for A1 adapter can be overridden by mounting a new
        # truststore (uncomment the lines below), whereas the corresponding password
        # should be updated in A1_TRUSTSTORE_PASSWORD environment variable (in the line above)
        #volumes:
        #  - ./a1_truststore.jks:/opt/onap/sdnc/data/stores/truststore.a1.adapter.jks:ro
        dns:
          - ${DNS_IP_ADDR-10.0.100.1}
        logging:
          driver:   "json-file"
          options:
            max-size: "30m"
            max-file: "5"
        extra_hosts:
            aaf.osaaf.org: 10.12.6.214

To start sdnc, run the following command ::

    oam/installation/src/main/yaml docker-compose up


Go to the "near-rt-ric-simulator/test/STD_1.1.3" folder in the "sim/a1-interface" repo and run the following command to
start the simulator. The script will give the container for the simulator the name "a1StdSimulator". ::

    near-rt-ric-simulator/test/STD_1.1.3 ./build_and_start.sh


The simulator needs to be added to the network that SDNC uses, named "yaml_default". To do this run the command ::

    docker network connect yaml_default a1StdSimulator

To test the A1 Adapter, navigate your browser to "http://localhost:8282/apidoc/explorer/index.html".

Use the username "admin" and the password "Kp8bJ4SXszM0WXlhak3eHlcse2gAw84vaoGGmJvUy2U" to log in.
Expand the "A1-ADAPTER-API" and open the "getA1Policy" post method.

In the input text area, paste the following and press the "Try it out!" button. ::

    {"input":{"near-rt-ric-url":"http://a1StdSimulator:8085/A1-P/v1/policies"}}

Hopefully the response is a Json that looks like the following ::

    {
      "output": {
        "http-status": 200,
        "body": "[]"
      }
    }

Configuration of certs
----------------------
The A1 adapter uses the default keystore and truststore that are built into the container.

The paths and passwords for these stores are located in a properties file:
 nonrtric/sdnc-a1-controller/oam/installation/src/main/properties/https-props.properties

The default truststore includes the a1simulator cert as a trusted cert which is located here:
 https://gerrit.o-ran-sc.org/r/gitweb?p=sim/a1-interface.git;a=tree;f=near-rt-ric-simulator/certificate;h=172c1e5aacd52d760e4416288dc5648a5817ce65;hb=HEAD

The default keystore, truststore, and https-props.properties files can be overridden by mounting new files using the "volumes" field of docker-compose. Uncommment the following lines in docker-compose to do this, and provide paths to the new files:

::

#volumes:
#   - <path_to_keystore>:/etc/ssl/certs/java/keystore.jks:ro
#   - <path_to_truststore>:/etc/ssl/certs/java/truststore.jks:ro
#   - <path_to_https-props>:/opt/onap/sdnc/data/properties/https-props.properties:ro

The target paths in the container should not be modified.

For example, assuming that the keystore, truststore, and https-props.properties files are located in the same directory as docker-compose:

`volumes:`
    `- ./new_keystore.jks:/etc/ssl/certs/java/keystore.jks:ro`

    `- ./new_truststore.jks:/etc/ssl/certs/java/truststore.jks:ro`

    `- ./new_https-props.properties:/opt/onap/sdnc/data/properties/https-props.properties:ro`

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

| The Policy Management Service can be accessed over the REST API. The REST API is documented in the
| *oran/a1-policy-management/docs/api.yaml* file. Please refer to the README file of Policy Management Service to know more about the API's.

Configuration of certs
----------------------
The Policy Management Service uses the default keystore and truststore that are built into the container. The paths and passwords for these stores are located in a yaml file:
 oran/a1-policy-management/config/application.yaml

The default truststore includes a1simulator cert as a trusted cert which is located here:
 https://gerrit.o-ran-sc.org/r/gitweb?p=sim/a1-interface.git;a=tree;f=near-rt-ric-simulator/certificate;h=172c1e5aacd52d760e4416288dc5648a5817ce65;hb=HEAD

The default truststore also includes a1controller cert as a trusted cert which is located here (keystore.jks file):
 https://gerrit.o-ran-sc.org/r/gitweb?p=nonrtric.git;a=tree;f=sdnc-a1-controller/oam/installation/sdnc-a1/src/main/resources;h=17fdf6cecc7a866c5ce10a35672b742a9f0c4acf;hb=HEAD

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
