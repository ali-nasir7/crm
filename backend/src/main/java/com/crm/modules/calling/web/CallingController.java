package com.crm.modules.calling.web;

import com.crm.modules.calling.domain.CallingDevice;
import com.crm.modules.calling.service.CallingService;
import com.crm.modules.calls.domain.Call;
import com.crm.modules.identity.service.PermissionKeys;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User-specific cellular calling. Every route is scoped to the logged-in user's own
 * devices; the bridge callback route is the only one authenticated by shared token.
 */
@RestController
@RequestMapping("/api/v1/calling")
@RequiredArgsConstructor
@Tag(name = "Calling")
public class CallingController {

    private final CallingService calling;

    public record DeviceRequest(String deviceName, String phoneNumber, String platform) {}
    public record CallRequest(UUID leadId, UUID contactId, String number, UUID deviceId) {}
    public record OutcomeRequest(String outcome, String notes, boolean createFollowUp, Instant followUpDueAt) {}
    public record BridgeStatusRequest(String ref, String state) {}
    public record BridgeHeartbeatRequest(java.util.UUID deviceId, String url) {}

    public record DeviceItem(UUID id, String deviceName, String phoneNumber, String platform,
                             String status, boolean isDefault, Instant lastSeenAt) {
        static DeviceItem from(CallingDevice d) {
            return new DeviceItem(d.getId(), d.getDeviceName(), d.getPhoneNumber(), d.getPlatform(),
                d.getStatus(), d.isDefault(), d.getLastSeenAt());
        }
    }

    public record CallStateItem(UUID id, String status, String outcome, String number,
                                UUID deviceId, Instant startedAt, Instant answeredAt,
                                Instant endedAt, Integer durationSeconds) {
        static CallStateItem from(Call c) {
            return new CallStateItem(c.getId(), c.getStatus(), c.getOutcome(), c.getNotes(),
                c.getDeviceId(), c.getStartedAt(), c.getAnsweredAt(), c.getEndedAt(), c.getDurationSeconds());
        }
    }

    // ---- devices ----

    @GetMapping("/devices")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CALL_VIEW + "')")
    public List<DeviceItem> myDevices() {
        return calling.list(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId())
            .stream().map(DeviceItem::from).toList();
    }

    @PostMapping("/devices")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CALL_CREATE + "')")
    public DeviceItem register(@RequestBody DeviceRequest request) {
        return DeviceItem.from(calling.register(CurrentUser.require().getOrganizationId(),
            CurrentUser.require().getId(), request.deviceName(), request.phoneNumber(), request.platform()));
    }

    @DeleteMapping("/devices/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CALL_CREATE + "')")
    public void delete(@PathVariable UUID id) {
        calling.delete(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), id);
    }

    @PostMapping("/devices/{id}/default")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CALL_CREATE + "')")
    public DeviceItem setDefault(@PathVariable UUID id) {
        return DeviceItem.from(calling.setDefault(CurrentUser.require().getOrganizationId(),
            CurrentUser.require().getId(), id));
    }

    @PostMapping("/devices/{id}/heartbeat")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CALL_CREATE + "')")
    public Map<String, String> heartbeat(@PathVariable UUID id) {
        calling.heartbeat(id, CurrentUser.require().getId());
        return Map.of("status", "ONLINE");
    }

    // ---- calls ----

    @PostMapping("/calls")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CALL_CREATE + "')")
    public CallStateItem call(@RequestBody CallRequest request) {
        return CallStateItem.from(calling.initiate(CurrentUser.require().getOrganizationId(),
            CurrentUser.require().getId(), request.leadId(), request.deviceId(), request.number()));
    }

    @GetMapping("/calls/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CALL_VIEW + "')")
    public CallStateItem state(@PathVariable UUID id) {
        Call c = calling.stateFor(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), id);
        return CallStateItem.from(c);
    }

    @PatchMapping("/calls/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CALL_CREATE + "')")
    public CallStateItem outcome(@PathVariable UUID id, @RequestBody OutcomeRequest request) {
        return CallStateItem.from(calling.finalize(CurrentUser.require().getOrganizationId(),
            CurrentUser.require().getId(), id, request.outcome(), request.notes(),
            request.createFollowUp(), request.followUpDueAt()));
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasAuthority('" + PermissionKeys.CALL_VIEW + "')")
    public Map<String, Object> analytics(@RequestParam(defaultValue = "30") int days) {
        return calling.analytics(CurrentUser.require().getOrganizationId(), CurrentUser.require().getId(), days);
    }

    // ---- bridge callback (shared-token auth; permitAll in SecurityConfig) ----

    @PostMapping("/bridge/status")
    public Map<String, String> bridgeStatus(@RequestHeader(value = "X-Bridge-Token", required = false) String token,
                                            @RequestBody BridgeStatusRequest request) {
        if (!calling.bridgeTokenValid(token)) {
            throw com.crm.common.api.ApiException.unauthorized("Invalid bridge token");
        }
        calling.applyBridgeState(request.ref(), request.state());
        return Map.of("ok", "true");
    }

    /** Bridge app announces it can reach its phone (shared token; sets device ONLINE + dial URL). */
    @PostMapping("/bridge/heartbeat")
    public Map<String, String> bridgeHeartbeat(@RequestHeader(value = "X-Bridge-Token", required = false) String token,
                                               @RequestBody BridgeHeartbeatRequest request) {
        if (!calling.bridgeTokenValid(token)) {
            throw com.crm.common.api.ApiException.unauthorized("Invalid bridge token");
        }
        calling.bridgeHeartbeat(request.deviceId(), request.url());
        return Map.of("ok", "true");
    }
}
