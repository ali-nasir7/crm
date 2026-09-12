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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only health probe for the Android Phone Bridge (GET {base}/status with the shared
 * token). Powers the "Call health" panel; never initiates anything and never logs or
 * returns the token itself. Deliberately NOT part of the {@link TelephonyService}
 * interface - health reporting is a bridge-specific concern and other providers would
 * expose their own diagnostics.
 *
 * Reported fields (all nullable when the bridge is unreachable):
 *   configured   - global crm.bridge.base-url is set
 *   reachable    - bridge answered /status with HTTP 2xx within the timeout
 *   latencyMs    - round trip of the status call
 *   version      - bridge build version
 *   phonePresent - adb sees the phone
 *   adbState     - "device" / "offline" / "no-device"
 *   simState     - "READY" / "ABSENT" / ... from getprop gsm.sim.state
 *   activeCalls  - calls the bridge currently tracks
 *   error        - human-readable reason when not reachable
 */
@Slf4j
@Component
public class BridgeHealthProbe {

    @Value("${crm.bridge.base-url:}")
    private String baseUrl;

    @Value("${crm.bridge.token:}")
    private String bridgeToken;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> probe() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean configured = baseUrl != null && !baseUrl.isBlank();
        out.put("configured", configured);
        if (!configured) {
            out.put("reachable", false);
            return out;
        }
        String statusUrl = baseUrl.replaceAll("/+$", "") + "/status";
        long t0 = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(statusUrl))
                .timeout(Duration.ofSeconds(4))
                .header("X-Bridge-Token", bridgeToken == null ? "" : bridgeToken)
                .header("Accept", "application/json")
                .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            out.put("latencyMs", (int) (System.currentTimeMillis() - t0));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                out.put("reachable", true);
                JsonNode n = mapper.readTree(resp.body());
                out.put("version", text(n, "version"));
                JsonNode phone = n.path("phone");
                out.put("phonePresent", phone.path("present").isBoolean() ? phone.get("present").asBoolean() : null);
                out.put("adbState", text(phone, "adbState"));
                out.put("simState", text(phone, "simState"));
                out.put("activeCalls", n.path("activeCalls").isInt() ? n.get("activeCalls").asInt() : null);
            } else {
                out.put("reachable", false);
                out.put("error", "Bridge answered HTTP " + resp.statusCode() + " (check CRM_BRIDGE_TOKEN on both sides)");
            }
        } catch (Exception e) {
            out.put("reachable", false);
            out.put("error", "Bridge not reachable at the configured URL: " + rootMessage(e));
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        return n.path(field).isMissingNode() || n.path(field).isNull() ? null : n.get(field).asText();
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
    }
}
