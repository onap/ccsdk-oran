#!/bin/bash

#  ============LICENSE_START===============================================
#  Copyright (C) 2021 Nordix Foundation. All rights reserved.
#  ========================================================================
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#  ============LICENSE_END=================================================

#Make the env vars availble to the robot scripts

unset http_proxy https_proxy
SHELL_FOLDER=$(cd "$(dirname "$0")";pwd)
docker stop $(docker ps -aq)
docker system prune -f

cd ${SHELL_FOLDER}/config
cp application_configuration.json.nosdnc application_configuration.json

cd ${SHELL_FOLDER}/

# start NONRTRIC containers with docker compose and configuration from docker-compose.yml
curl -L https://github.com/docker/compose/releases/download/1.27.0/docker-compose-`uname -s`-`uname -m` > docker-compose
chmod +x docker-compose
./docker-compose up -d

ROBOT_VARIABLES="-b debug.log -v SCRIPTS:${SCRIPTS}"

