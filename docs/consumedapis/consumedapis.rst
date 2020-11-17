.. SPDX-License-Identifier: CC-BY-4.0
.. Copyright 2020 Nordix Foundation

Consumed APIs
=============


Policy Management Service application is interacting with two ONAP APIs and the A1-P API.

*******
CBS API
*******

The CBS API is used to get the dynamic configuration of the service, such as available Near-RT RICs.

::

    CBS_GET_ALL

*********
DMAAP API
*********

The DMaaP API is used to provide the possibility to interact with the Policy Management Service through DMaaP Message
Router.

::

    DMAAP_GET_EVENTS

********
A1-P API
********

The A1-P API is used to communicate with the Near-RT RICs (north bound). All endpoints of the OSC A1 REST API and the
standard A1 REST API version 1.1 are used.
