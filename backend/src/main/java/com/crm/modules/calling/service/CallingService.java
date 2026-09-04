package com.crm.modules.calling.service;

import com.crm.common.api.ApiException;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.calling.domain.CallingDevice;
import com.crm.modules.calling.repo.CallingDeviceRepository;
import com.crm.modules.calls.domain.Call;
import com.crm.modules.calls.repo.CallRepository;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.service.LeadAccessPolicy;
import com.crm.modules.telephony.TelephonyService;
import com.crm.modules.tasks.dto.TaskDtos.CreateTaskRequest;
import com.crm.modules.tasks.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User-specific cellular calling: "call this number with MY phone".
 * Devices belong to exactly one user; nobody can ring somebody else's device.
 * The bridge reports state changes asynchronously; terminal states finalize the
 * Call row (timestamps, duration, outcome) and append the activity-timeline entry.
 */
@Service
@RequiredArgsConstructor
public class CallingService {

    /** A device counts as ONLINE only with a fresh heartbeat. */
    private static final Duration HEARTBEAT_FRESHNESS = Duration.ofMinutes(2);

    public static final List<String> OUTCOMES = List.of(
        "NO_ANSWER", "BUSY", "WRONG_NUMBER", "INTERESTED", "NOT_INTERESTED", "CALL_BACK_LATER", "MEETING_BOOKED");

    private final CallingDeviceRepository devices;
    private final CallRepository calls;
    private final UserRepository users;
    private final LeadAccessPolicy leadAccess;
    private final ActivityService activities;
    private final TaskService taskService;
    private final TelephonyService telephony;

    @Value("${crm.bridge.token:}")
    private String bridgeToken;

    // ---- devices ------------------------------------------------------------

    @Transactional
    public CallingDevice register(UUID orgId, UUID userId, String deviceName, String phoneNumber, String platform) {
        String name = deviceName == null ? "" : deviceName.trim();
        if (name.isEmpty() || name.length() > 80) throw ApiException.badRequest("Device name is required (max 80 chars)");
        boolean first = devices.findByOrganizationIdAndUserId(orgId, userId).isEmpty();
        CallingDevice d = new CallingDevice();
        d.setOrganizationId(orgId);
        d.setUserId(userId);
        d.setDeviceName(name);
        d.setPhoneNumber(phoneNumber);
        d.setPlatform(platform == null ? "ANDROID" : platform);
        d.setStatus("OFFLINE");
        d.setDefault(first);
        return devices.save(d);
    }

    @Transactional(readOnly = true)
    public List<CallingDevice> list(UUID orgId, UUID userId) {
        List<CallingDevice> out = devices.findByOrganizationIdAndUserIdOrderByCreatedAtAsc(orgId, userId);
        out.forEach(this::applyEffectiveStatus);
        return out;
    }

    @Transactional
    public void delete(UUID orgId, UUID userId, UUID deviceId) {
        CallingDevice d = devices.findOwned(orgId, userId, deviceId)
            .orElseThrow(() -> ApiException.notFound("Device not found"));
        devices.delete(d);
    }

    @Transactional
    public CallingDevice setDefault(UUID orgId, UUID userId, UUID deviceId) {
        CallingDevice d = devices.findOwned(orgId, userId, deviceId)
            .orElseThrow(() -> ApiException.notFound("Device not found"));
        for (CallingDevice other : devices.findByOrganizationIdAndUserId(orgId, userId)) {
            other.setDefault(other.getId().equals(d.getId()));
            devices.save(other);
        }
        return d;
    }

    /** Liveness ping from the bridge app (shared token) or the device owner while testing. */
    @Transactional
    public void heartbeat(UUID deviceId, UUID ownerCheck) {
        CallingDevice d = devices.findById(deviceId)
            .orElseThrow(() -> ApiException.notFound("Device not found"));
        if (ownerCheck != null && !d.getUserId().equals(ownerCheck)) {
            throw ApiException.forbidden("This device belongs to another user");
        }
        d.setLastSeenAt(Instant.now());
        d.setStatus("ONLINE");
        devices.save(d);
    }

    /**
     * Liveness + address announcement from the bridge app itself (shared bridge token, no
     * user JWT - this runs headless on the user's PC). Marks the device ONLINE while the
     * bridge can actually reach the phone, and stores the URL the backend must dial through.
     * Trust model: whoever holds CRM_BRIDGE_TOKEN may set this; treat that token as a secret.
     */
    @Transactional
    public void bridgeHeartbeat(UUID deviceId, String url) {
        CallingDevice d = devices.findById(deviceId)
            .orElseThrow(() -> ApiException.notFound("Device not found for bridge heartbeat"));
        d.setLastSeenAt(Instant.now());
        d.setStatus("ONLINE");
        if (url != null && !url.isBlank()) d.setBridgeUrl(url.trim());
        devices.save(d);
    }

    // ---- calling ------------------------------------------------------------

    @Transactional
    public Call initiate(UUID orgId, UUID userId, UUID leadId, UUID requestedDeviceId, String rawNumber) {
        String number = rawNumber == null ? "" : rawNumber.replaceAll("[\\s\\-()]", "");
        if (!number.matches("\\+?\\d{7,15}")) {
            throw ApiException.badRequest("Enter a valid phone number (7-15 digits, optional + prefix)");
        }
        List<CallingDevice> mine = devices.findByOrganizationIdAndUserId(orgId, userId);
        if (mine.isEmpty()) {
            throw ApiException.business("No calling device registered for your account. Add one under Calls > My calling devices.");
        }
        CallingDevice device = requestedDeviceId != null
            ? devices.findOwned(orgId, userId, requestedDeviceId)
                .orElseThrow(() -> ApiException.forbidden("Device not found for your account"))
            : mine.stream().filter(CallingDevice::isDefault).findFirst().orElse(mine.get(0));
        applyEffectiveStatus(device);
        if (!"ONLINE".equals(device.getStatus())) {
            throw ApiException.business("Calling device \"" + device.getDeviceName()
                + "\" is offline. Start the bridge app, or pick another device.");
        }
        String customerName = null;
        if (leadId != null) {
            customerName = leadAccess.loadVisible(orgId, leadId).getBusinessName(); // 404-masked visibility check
        }

        TelephonyService.InitiationResult result;
        try {
            result = telephony.initiate(new TelephonyService.CallCommand(
                orgId, userId, device.getId(), device.getDeviceName(), device.getPhoneNumber(),
                number, customerName, device.getBridgeUrl()));
        } catch (TelephonyService.TelephonyException e) {
            throw ApiException.business(e.getMessage());
        }

        Instant now = Instant.now();
        Call call = new Call();
        call.setOrganizationId(orgId);
        call.setUserId(userId);
        call.setLeadId(leadId);
        call.setDeviceId(device.getId());
        call.setDirection("OUTGOING");
        call.setOccurredAt(now);
        call.setStartedAt(now);
        call.setStatus("INITIATING");
        call.setOutcome("IN_PROGRESS");
        call.setProviderRef(result.providerRef());
        call.setNotes(number);
        calls.save(call);
        return call;
    }

    /** Bridge callback: RINGING, CONNECTED, ENDED, NO_ANSWER, BUSY, FAILED. */
    @Transactional
    public void applyBridgeState(String providerRef, String state) {
        Call call = calls.findFirstByProviderRef(providerRef)
            .orElseThrow(() -> ApiException.notFound("Call not found for bridge reference"));
        Instant now = Instant.now();
        switch (state == null ? "" : state.toUpperCase()) {
            case "RINGING" -> call.setStatus("RINGING");
            case "CONNECTED" -> { call.setStatus("CONNECTED"); call.setAnsweredAt(now); }
            case "ENDED" -> finalizeCall(call, "CONNECTED", now);
            case "NO_ANSWER" -> finalizeCall(call, "NO_ANSWER", now);
            case "BUSY" -> finalizeCall(call, "BUSY", now);
            case "FAILED" -> finalizeCall(call, "FAILED", now);
            default -> throw ApiException.badRequest("Unknown call state: " + state);
        }
        calls.save(call);
    }

    @Transactional(readOnly = true)
    public Call stateFor(UUID orgId, UUID userId, UUID callId) {
        return calls.findById(callId)
            .filter(c -> c.getOrganizationId().equals(orgId) && c.getUserId().equals(userId))
            .orElseThrow(() -> ApiException.notFound("Call not found"));
    }

    /** Rep sets the business outcome after the call (and optionally books a follow-up). */
    @Transactional
    public Call finalize(UUID orgId, UUID userId, UUID callId, String outcome, String notes,
                         boolean createFollowUp, java.time.Instant followUpDueAt) {
        Call call = calls.findById(callId)
            .filter(c -> c.getOrganizationId().equals(orgId) && c.getUserId().equals(userId))
            .orElseThrow(() -> ApiException.notFound("Call not found"));
        if (!OUTCOMES.contains(outcome)) throw ApiException.badRequest("Invalid call outcome: " + outcome);
        finalizeCall(call, outcome, Instant.now());
        if (notes != null) call.setNotes(notes);
        calls.save(call);

        if (call.getLeadId() != null) {
            activities.record(orgId,
                ActivityType.OUTGOING_CALL,
                call.getLeadId(),
                "Call: " + outcome,
                notes,
                Map.of("outcome", outcome,
                       "durationSeconds", call.getDurationSeconds() == null ? 0 : call.getDurationSeconds(),
                       "device", String.valueOf(call.getDeviceId())),
                userId);
        }
        if (createFollowUp && "CALL_BACK_LATER".equals(outcome)) {
            String title = "Call back" + (call.getLeadId() != null ? " lead" : " customer") + " (from call " + call.getId() + ")";
            taskService.create(orgId, userId, new CreateTaskRequest(
                title, "Follow-up created from call outcome CALL_BACK_LATER",
                call.getLeadId(), null, call.getContactId(), "FOLLOW_UP",
                userId, followUpDueAt != null ? followUpDueAt : Instant.now().plus(Duration.ofDays(1)),
                "MEDIUM"));
        }
        return call;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analytics(UUID orgId, UUID userId, int days) {
        Instant from = Instant.now().minus(Duration.ofDays(days));
        Map<String, Object> m = new LinkedHashMap<>();
        long total = 0;
        long totalSeconds = 0;
        for (String outcome : OUTCOMES) {
            long n = calls.countByOrganizationIdAndUserIdAndOutcomeAndOccurredAtBetween(orgId, userId, outcome, from, Instant.now());
            m.put(outcome, n);
            total += n;
        }
        for (Call c : calls.findByOrganizationIdAndUserIdAndOccurredAtBetween(orgId, userId, from, Instant.now())) {
            if (c.getDurationSeconds() != null) totalSeconds += c.getDurationSeconds();
        }
        m.put("total", total);
        m.put("totalDurationSeconds", totalSeconds);
        return m;
    }

    /** Shared-token check for the bridge callback endpoint. Blank token = endpoint disabled. */
    public boolean bridgeTokenValid(String presented) {
        if (bridgeToken == null || bridgeToken.isBlank() || presented == null) return false;
        return MessageDigest.isEqual(
            bridgeToken.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    private void finalizeCall(Call call, String outcome, Instant endedAt) {
        call.setStatus("ENDED");
        call.setEndedAt(endedAt);
        call.setOutcome(outcome);
        if (call.getStartedAt() != null) {
            call.setDurationSeconds((int) Duration.between(call.getStartedAt(), endedAt).getSeconds());
        }
        if (call.getAnsweredAt() == null && "CONNECTED".equals(outcome)) call.setAnsweredAt(call.getStartedAt());
    }

    private void applyEffectiveStatus(CallingDevice d) {
        boolean fresh = d.getLastSeenAt() != null && d.getLastSeenAt().isAfter(Instant.now().minus(HEARTBEAT_FRESHNESS));
        d.setStatus(fresh ? "ONLINE" : "OFFLINE");
    }
}
