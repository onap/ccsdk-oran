#!/usr/bin/env bash
###############################################################################
# Copyright 2017 Huawei Technologies Co., Ltd.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Modifications copyright (c) 2021 Nordix Foundation
#
###############################################################################

unset http_proxy https_proxy
SHELL_FOLDER=$(cd "$(dirname "$0")";pwd)
docker stop $(docker ps -aq)
docker system prune -f

cd ${SHELL_FOLDER}/../config
cp application_configuration.json.nosdnc application_configuration.json

cd ${SHELL_FOLDER}/../

# start NONRTRIC containers with docker compose and configuration from docker-compose.yml
curl -L https://github.com/docker/compose/releases/download/1.29.0/docker-compose-`uname -s`-`uname -m` > docker-compose
chmod +x docker-compose
./docker-compose --env-file .env up -d


checkStatus(){
    for ((i=0; i<$1; i++)); do
        res=$($2)
        echo "$res"
        expect=$3
        if [ "$res" == "$expect" ]; then
            echo -e "$i sec: $4 is alive!\n"
            return 0
            break;
        else
            sleep 1
        fi
    done
    echo -e "si sec: $4 is NOT alive!\n"
    return -1
}
# Healthcheck docker containers

# check SIM1 status
echo "check SIM1 status:"
checkStatus 120 "curl -Skw %{http_code} http://localhost:30001/" "OK200" "SIM1"

# check SIM2 status
echo "check SIM2 status:"
checkStatus 120 "curl -Skw %{http_code} http://localhost:30003/" "OK200" "SIM2"

# check SIM3 status
echo "check SIM3 status:"
checkStatus 120 "curl -Skw %{http_code} http://localhost:30005/" "OK200" "SIM3"

# check PMS status
echo "check PMS status:"
checkStatus 120 "curl -Skw %{http_code} http://localhost:8081/status" "success200" "PMS"

echo "NONRTRIC health check passed."
