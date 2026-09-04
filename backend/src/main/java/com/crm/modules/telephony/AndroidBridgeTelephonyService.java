package com.crm.modules.telephony;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * V1 provider: talks to the Android Phone Bridge, a small companion app running next to the
 * user's phone (PC <-> Android via the vendor link, e.g. Phone Link, handles audio; THIS
 * bridge only receives the dial command). The bridge contract is intentionally tiny:
 *
 *   POST {base-url}/call
 *   Header: X-Bridge-Token: {crm.bridge.token}
 *   Body:   { "deviceId": "...", "number": "+9715...", "customerName": "..." }
 *   200 ->  { "ref": "bridge-call-id", "state": "RINGING" }
 *
 * State changes flow back through POST /api/v1/calling/bridge/status (authenticated with
 * the same shared token). A missing/blank URL or token simply makes the feature report
 * "Integration Required" instead of breaking anything.
 */
@Slf4j
@Component
public class AndroidBridgeTelephonyService implements TelephonyService {

    public static final String PROVIDER_ID = "ANDROID_BRIDGE_V1";

    @Value("${crm.bridge.base-url:}")
    private String baseUrl;

    @Value("${crm.bridge.token:}")
    private String bridgeToken;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Boot-time visibility: which calling mode is active (never logs the token itself). */
    @org.springframework.boot.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void reportConfigState() {
        if (available()) {
            log.info("Calling provider {}: configured with global bridge URL {}", PROVIDER_ID, baseUrl);
        } else if (bridgeToken != null && !bridgeToken.isBlank()) {
            log.info("Calling provider {}: token set, no global URL - each device routes through the bridge "
                + "URL it announces via heartbeat. Run bridge/android-bridge.js next to the phone.", PROVIDER_ID);
        } else {
            log.info("Calling provider {}: NOT configured - Call will report Integration Required. "
                + "Set CRM_BRIDGE_TOKEN (same value in bridge .env) and run bridge/android-bridge.js.", PROVIDER_ID);
        }
    }

    @Override
    public String providerId() { return PROVIDER_ID; }

    @Override
    public boolean available() {
        // Global default only. Per-device bridge URLs (announced via heartbeat) override
        // this at call time, so a deployment without crm.bridge.base-url still works.
        return baseUrl != null && !baseUrl.isBlank() && bridgeToken != null && !bridgeToken.isBlank();
    }

    @Override
    public InitiationResult initiate(CallCommand cmd) throws TelephonyException {
        String base = cmd.bridgeUrl() != null && !cmd.bridgeUrl().isBlank()
            ? cmd.bridgeUrl().trim() : baseUrl;
        if (base == null || base.isBlank() || bridgeToken == null || bridgeToken.isBlank()) {
            throw new TelephonyException("Android bridge is not configured (Integration Required): "
                + "start bridge/android-bridge.js next to your phone (it announces its URL via heartbeat), "
                + "or set CRM_BRIDGE_URL and CRM_BRIDGE_TOKEN on the backend.");
        }
        try {
            String body = mapper.writeValueAsString(java.util.Map.of(
                "deviceId", cmd.deviceId() == null ? "" : cmd.deviceId().toString(),
                "number", cmd.toNumber(),
                "customerName", cmd.customerName() == null ? "" : cmd.customerName()));
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/call"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Bridge-Token", bridgeToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TelephonyException("Bridge rejected the call command (HTTP " + response.statusCode() + ")");
            }
            JsonNode json = mapper.readTree(response.body());
            String ref = json.path("ref").asText(null);
            String state = json.path("state").asText("RINGING");
            return new InitiationResult(ref, state);
        } catch (TelephonyException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Android bridge call failed: {}", e.getMessage());
            throw new TelephonyException("Cannot reach the Android bridge: " + e.getMessage());
        }
    }
}
