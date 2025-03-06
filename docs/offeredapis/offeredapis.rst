.. This work is licensed under a Creative Commons Attribution 4.0 International License.
.. http://creativecommons.org/licenses/by/4.0
.. Copyright 2022-2024 Nordix Foundation. All rights reserved.
.. Copyright 2024-2025 OpenInfra Foundation Europe. All rights reserved.


.. _offered_apis:


Offered APIs
============

Introduction
------------

The north-bound REST API of the A1 Policy Management Service provides convenient methods to handle A1 policies.


Overall architecture for O-RAN A1 Policy functions
--------------------------------------------------

This picture provides a overview of ONAP's A1 Controller architecture,
integration with other ONAP components and API resource/operation provided.

.. image:: ../media/ONAP-A1ControllerArchitecture-Paris.png
   :width: 500pt

API Table
---------

.. |swagger-icon| image:: ../media/swagger.png
                  :width: 40px

.. |yaml-icon| image:: ../media/yaml_logo.png
                  :width: 40px

.. |html-icon| image:: ../media/html_logo.png
                  :width: 40px
                  
.. csv-table::
   :header: "API name", "|swagger-icon|", "|yaml-icon|", "|html-icon|"
   :widths: 56,11,11,22

   "*New* **A1 Policy Management API**: A1PolicyManagementService, ServiceRegistry, Configuration, NearRT-RIC Repository, and Health Check APIs (NBI)", ":download:`link <./swagger/pms-api-v3.json>`", ":download:`link <./swagger/pms-api-v3.yaml>`", "`A1 Policy Management API <./pms-api-v3.html>`_"
   "*Older* **Pre-Spec A1 Policy Management API**: Older A1PolicyManagementService, ServiceRegistry, Configuration, NearRT-RIC Repository, Health Check APIs, and Admin/Actuator APIs (NBI)", ":download:`link <./swagger/pms-api.json>`", ":download:`link <./swagger/pms-api.yaml>`", "`Pre-Spec A1 Policy Management API <./pms-api.html>`_ "
   "A1 ADAPTER API (*Internal Only*)", ":download:`link <./swagger/a1-adapter-api.json>`", ":download:`link <./swagger/a1-adapter-api.yaml>`", "`A1 ADAPTER API (Internal Only) <./a1-adapter-api.html>`_"

.. _pms_api:

A1 Policy Management Service APIs
.................................

| The *New* **A1 Policy Management API**  includes the latest APIs (NBI) for: A1 Policy Management, Service Registry, Configuration, NearRT-RIC Repository, and Health Check. 
| This A1 Policy Management API is described in more detail in: `A1 Policy Management API (html) <./pms-api-v3.html>`_

| The *Older* **Pre-Spec A1 Policy Management API** includes older versions of the APIs for: A1 Policy Management, Service Registry, Configuration, NearRT-RIC Repository, Health Check, and Admin/Actuator functions. 
| These APIs may be deprecated in future versions. 
| This *Older Pre-Spec* A1 Policy Management API is described in more detail in: `Pre-Spec A1 Policy Management API (html) <./pms-api.html>`_ 

.. _a1_adapter_api:

A1 ADAPTER API
..............

| The O-RAN A1 Adapter provides an **internal** RESTCONF API that is used by the A1 Policy Management Service when accessing the A1 Interface. 
| This API is useful for test and verification purposes but should not be used otherwise.
| The A1 Adapter API is described in more detail in `A1 ADAPTER API (html) <./a1-adapter-api.html>`_
