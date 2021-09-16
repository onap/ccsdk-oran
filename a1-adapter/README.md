# ONAP A1 Policy Adapter

A1 Policy provides a Northbound Interface for A1 operations to do policy management in Near-RealTime RICs.
This is the parent repo which has submodules that creates the bundles, features & installers for A1 Policy Operation. 

This makes it very fast to start the controller and also puts less load on the CPU.

This adapter is designed to be added to ONAP CCSDK controllers (e.g. SDNC) thus providing support for mediating connections the A1 interface to/from RAN functions.

It is also possible to configure a HTTP Proxy for southbound connections. In order to configure the proxy, variable a1Mediator.proxy.url in file odlsli/src/main/properties/a1-adapter-api-dg.properties must contain the URL of the proxy. This file is part of the CCSDK/distribution repository.  

## License

Copyright (C) 2020 Nordix Foundation.
Licensed under the Apache License, Version 2.0 (the "License")
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

For more information about license please see the [LICENSE](LICENSE.txt) file for details.
