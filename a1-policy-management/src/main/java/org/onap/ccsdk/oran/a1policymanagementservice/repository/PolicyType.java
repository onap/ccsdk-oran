/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2020 Nordix Foundation. All rights reserved.
 * ======================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================LICENSE_END===================================
 */

package org.onap.ccsdk.oran.a1policymanagementservice.repository;

import lombok.Builder;
import lombok.Getter;

import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.springframework.http.HttpStatus;

@Builder
public class PolicyType {
    @Getter
    private String id;
    @Getter
    private String schema;

    @Getter
    public static class Version {
        public final int major;
        public final int minor;
        public final int patch;

        public Version(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        public static Version ofString(String version) throws ServiceException {
            String[] versionTokenized = version.split("\\.");
            if (versionTokenized.length != 3) {
                throw new ServiceException("Version must contain major.minor.patch code: " + version,
                        HttpStatus.BAD_REQUEST);
            }

            try {
                return new Version( //
                        Integer.parseInt(versionTokenized[0]), //
                        Integer.parseInt(versionTokenized[1]), //
                        Integer.parseInt(versionTokenized[2]) //
                );
            } catch (Exception e) {
                throw new ServiceException("Syntax error in " + version, HttpStatus.BAD_REQUEST);
            }
        }
    }

    @Getter
    public static class TypeId {
        private final String name;
        private final String version;

        public TypeId(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public static TypeId ofString(String typeId) {
            StringBuilder name = new StringBuilder();
            String version = "";
            String[] tokens = typeId.split("_");

            if (tokens.length >= 2) {
                version = tokens[tokens.length - 1]; // Last token
                for (int i = 0; i < tokens.length - 1; ++i) {
                    if (i != 0) {
                        name.append("_");
                    }
                    name.append(tokens[i]); // All other tokens
                }
                return new TypeId(name.toString(), version);
            } else {
                return new TypeId(typeId, "");
            }
        }
    }

    public TypeId getTypeId() {
        return TypeId.ofString(getId());
    }

}
