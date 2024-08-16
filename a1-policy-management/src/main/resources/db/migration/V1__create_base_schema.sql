-- ============LICENSE_START=======================================================
-- Copyright (C) 2024 OpenInfra Foundation Europe
-- ================================================================================
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--       http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
-- SPDX-License-Identifier: Apache-2.0
-- ============LICENSE_END=========================================================

CREATE TABLE IF NOT EXISTS policies (
	id varchar NOT NULL,
	payload varchar NULL,
	CONSTRAINT policies_pk PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS policy_types (
	id varchar NOT NULL,
	payload varchar NULL,
	CONSTRAINT policy_types_pk PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS services (
	id varchar NOT NULL,
	payload varchar NULL,
	CONSTRAINT services_pk PRIMARY KEY (id)
);