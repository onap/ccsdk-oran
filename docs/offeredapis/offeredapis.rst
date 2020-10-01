.. This work is licensed under a Creative Commons Attribution 4.0
   International License.
.. http://creativecommons.org/licenses/by/4.0
.. Copyright 2020 Nordix Foundation
.. _offeredapis:


============
Offered APIs
============
************
Introduction
************

The north bound ...

************************
Global ORAN architecture
************************

Following illustration provides a global view about **ORAN** architecture,
integration with other ONAP components and API resource/operation provided.

.. image:: ../images/onap_ccsdk_oran.jpg
   :width: 800px

************
API Endpoint
************

TBD

***********
API Version
***********

APIs are described with a  state version with "v" following the API Name,
e.g.:  ``TBD``.
The schema associated with a REST API must have its version number aligned
with that of the REST API.

The version number has major, minor and revision numbers. E.g. v1.0.0
The version number (without the revision number) is held in the URI.

The major version number is incremented for an incompatible change.
The minor version number is incremented for a compatible change.
For minor modifications of the API, version numbering must not be updated,
provided the following  backward compatibility rules are respected:

- New elements in a data type must be optional (``minOccurs=0``)
- Changes in the cardinality of an attribute in a data type must be from
  mandatory to optional or from lower to greater
- New attributes defined in an element must be optional (absence of
  ``use="required"``).
- If new enumerated values are included, the former ones and its meaning must
  be kept.
- If new operations are added, the existing operations must be kept
- New parameters added to existing operations must be optional and existing
  parameters must be kept

For major modifications of the API, not backward compatible and forcing client
implementations to be changed, the version number must be updated.

*********
API Table
*********

.. |swagger-icon| image:: images/swagger.png
                  :width: 40px



.. csv-table::
   :header: "API", "|swagger-icon|", "|swagger-icon|"
   :widths: 10,5,5

   " ", "json file", "yaml file"
   "policyManagementService", ":download:`link <api_policyManagementService/swagger.json>`", ":download:`link <api_policyManagementService/swagger.yaml>`"
   "a1Adapter", ":download:`link <api_a1Adapter/swagger.json>`", ":download:`link <api_a1Adapter/swagger.yaml>`"

***********************
API ReDoc Documentation
***********************

* :doc:`Policy Management Service <redoc/api_policyManagementService>`
* :doc:`A1 Adapter <redoc/api_a1Adapter>`


***************
API Description
***************

-----------------------
policyManagementService
-----------------------

This API ...

---------
a1Adapter
---------

This API ...

***************
Developer Guide
***************

Technical information about **ORAN** components (dependencies, configuration, running &
testing) could be found here:
:doc:`ORAN_Developer_Guide <../architecture/ORAN_Developer_Guide>`
