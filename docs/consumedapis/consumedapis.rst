.. SPDX-License-Identifier: CC-BY-4.0
.. Copyright 2023 Nordix Foundation

Consumed APIs
=============


The A1 Policy Management Service consumes two ONAP APIs and the A1-P API.


*****************************************
O-RAN A1 Interface for A1 Policies (A1-P)
*****************************************

Southbound, the ONAP A1 Policy functions communicate with *near-RT RIC* RAN functions using the **A1** interface, as defined by the `O-RAN Alliance <https://www.o-ran.org>`_
The *A1 Interface - Application Protocol Specification (A1-AP)* describes this interface. The specification can be viewed from the `O-RAN Alliance <https://www.o-ran.org>`_ website.

The **London** ONAP A1 Policy functions implement the *A1 Policy* (*A1-P*) parts of A1-AP, supporting versions *v1.1*, *v2.0* and *v3.0*.

An opensource implementation of a `near-RT RIC <https://lf-o-ran-sc.atlassian.net/wiki/spaces/RICP/overview>`_ is available from the `O-RAN Software Community <https://o-ran-sc.org>`_. It supports a pre-spec version of the A1-AP. The ONAP A1 Policy functions described here also supports this A1 version (*A1-OSC*).

An opensource implementation of an `A1 Simulator <https://lf-o-ran-sc.atlassian.net/wiki/spaces/RICNR/overview>`_ is also available from the `O-RAN Software Community <https://o-ran-sc.org>`_. It supports all versions of A1-AP.