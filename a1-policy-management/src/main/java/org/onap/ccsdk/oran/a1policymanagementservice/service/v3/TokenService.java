/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2025 OpenInfra Foundation Europe. All rights reserved.
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

 package org.onap.ccsdk.oran.a1policymanagementservice.service.v3;

 import java.lang.invoke.MethodHandles;
 import java.util.Base64;

 import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyInfo;
 import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyObjectInformation;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.http.HttpHeaders;
 import org.springframework.stereotype.Service;
 import org.springframework.web.server.ServerWebExchange;

 import com.google.gson.JsonObject;
 import com.google.gson.JsonParser;

 @Service
 public class TokenService {
     private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

     // Prefix used to identify Bearer tokens in the Authorization header
     private static final String BEARER_PREFIX = "Bearer ";


     /**
      * Retrieves the service ID for version 3 (v3) of the API, which uses PolicyObjectInformation.
      *
      * @param policyInfoValue The PolicyObjectInformation object containing the policy details.
      * @param exchange The ServerWebExchange object that contains request and response information.
      * @return The service ID, either from the policy information or derived from the client ID in the token.
      */
     public String getServiceId(PolicyObjectInformation policyInfoValue, ServerWebExchange exchange) {
         String serviceId = policyInfoValue.getServiceId();
         String clientId = extractClientIdFromToken(exchange);

         // If the service ID from the policy is blank, use the client ID from the token instead
         if (serviceId.isBlank()) {
             if (clientId != null && !clientId.isBlank()) {
                 serviceId = clientId;
             }
         }
         // Return the determined service ID
         logger.debug("ServiceID extracted from token: "  + serviceId);
         return serviceId;
     }

     /**
      * Retrieves the service ID for version 2 (v2) of the API, which uses PolicyInfo.
      *
      * @param policyInfoValue The PolicyInfo object containing the policy details.
      * @param exchange The ServerWebExchange object that contains request and response information.
      * @return The service ID, either from the policy information or derived from the client ID in the token.
      */
     public String getServiceId(PolicyInfo policyInfoValue, ServerWebExchange exchange) {
         String serviceId = policyInfoValue.getServiceId();
         String clientId = extractClientIdFromToken(exchange);

         // If the service ID from the policy is blank, use the client ID from the token instead
         if (serviceId.isBlank()) {
             if (clientId != null && !clientId.isBlank()) {
                 serviceId = clientId;
             }
         }
         // Return the determined service ID
         logger.debug("ServiceID extracted from token: "  + serviceId);
         return serviceId;
     }

     /**
      * Extracts the client ID from the Bearer token present in the Authorization header.
      *
      * @param exchange The ServerWebExchange object that contains request and response information.
      * @return The client ID extracted from the token, or null if the token is invalid or missing.
      */
     private String extractClientIdFromToken(ServerWebExchange exchange) {
         HttpHeaders headers = exchange.getRequest().getHeaders();
         String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);

         // Check if the Authorization header exists and contains a Bearer token
         if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
             String token = authHeader.substring(BEARER_PREFIX.length());
             return decodeClientId(token);
         } else {
             // Log a debug message if the Authorization header is missing or invalid
             logger.debug("Authorization header is missing or does not contain a Bearer token");
         }
         return null;
     }

     /**
      * Decodes the client ID from the JWT token.
      *
      * @param token The JWT token string.
      * @return The client ID extracted from the token, or null if decoding fails.
      */
     private String decodeClientId(String token) {
         try {
             // Split the JWT token to get the payload part
             String[] chunks = token.split("\\.");
             Base64.Decoder decoder = Base64.getUrlDecoder();
             String payload = new String(decoder.decode(chunks[1]));
             JsonObject jsonObject = JsonParser.parseString(payload).getAsJsonObject();

             // Return the client ID from the payload
             return jsonObject.get("client_id").getAsString();
         } catch (Exception e) {
             // Log an error if decoding fails
             logger.error("Error decoding client ID from token", e);
             return null;
         }
     }
 }
