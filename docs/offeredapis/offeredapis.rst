.. This work is licensed under a Creative Commons Attribution 4.0
   International License.
.. http://creativecommons.org/licenses/by/4.0
.. Copyright 2020 Nordix Foundation

.. _offered_apis:


Offered APIs
============

Introduction
************

The north bound REST API of the Policy Management Service provides convenient methods to handle policies.


Overview
************************

Following illustration provides a global view about **ORAN** architecture,
integration with other ONAP components and API resource/operation provided.

.. image:: ../media/ONAP-A1ControllerArchitecture.png
   :width: 500pt


API Version
***********

APIs are described with a  state version with "v" following the API Name,
e.g.:  ``v2/policy``.
The schema associated with a REST API must have its version number aligned
with that of the REST API.

The version number has major, minor and revision numbers. E.g. v1.0.0
The version number (without the revision number) is held in the URI.

The major version number is incremented for an incompatible change.
The minor version number is incremented for a compatible change.
For minor modifications of the API, version numbering must not be updated,

For major modifications of the API, not backward compatible and forcing client
implementations to be changed, the major version number must be updated.


API Table
*********

.. |swagger-icon| image:: ../media/swagger.png
                  :width: 40px

.. |yaml-icon| image:: ../media/yaml_logo.png
                  :width: 40px


.. csv-table::
   :header: "API name", "|swagger-icon|", "|yaml-icon|"
   :widths: 10,5, 5

   "PMS API", ":download:`link <./swagger/pms-api.json>`", ":download:`link <./swagger/pms-api.yaml>`"
   "A1 ADAPTER API (Internal)", ":download:`link <./swagger/a1-adapter-api.json>`", ":download:`link <./swagger/a1-adapter-api.yaml>`"


.. _pms_api:

PMS API
.......
`PMS API <./pms-api.html>`_

.. _a1_adapter_api:

A1 ADAPTER API
..............
`A1 ADAPTER API (Internal) <./a1-adapter-api.html>`_

