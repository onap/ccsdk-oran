.. SPDX-License-Identifier: CC-BY-4.0
.. Copyright 2021 Nordix Foundation

Consumed APIs
=============


Policy Management Service application is interacting with two ONAP APIs and the A1-P API.


*********
DMAAP API
*********

The A1 Policy Management Service API can also be accessed using *ONAP DMaaP*. To support this the `DMaaP Message Router API <https://docs.onap.org/projects/onap-dmaap-messagerouter-messageservice/en/latest/offeredapis/api.html>`_ is used.  

*****************************************
O-RAN A1 interface for A1 Policies (A1-P)
*****************************************

Southbound, the ONAP A1 Policy functions communicate with *near-RT-RIC* RAN functions using the **A1** interface, as defined by the `O-RAN Alliance <https://www.o-ran.org>`_   
The *A1 Interface - Application Protocol Specification (A1-AP)* describe this interface. The specification can be viewed from the `O-RAN Alliance <https://www.o-ran.org>`_ website. 

    
An opensource implementation of a `near-RT-RIC <https://wiki.o-ran-sc.org/pages/viewpage.action?pageId=1179659>`_ is available from `O-RAN Software Community <https://o-ran-sc.org>`_. It supports a pre-spec version of the A1-AP. The ONAP A1 Policy functions described here also supports this A1 version (A1-OSC). 

