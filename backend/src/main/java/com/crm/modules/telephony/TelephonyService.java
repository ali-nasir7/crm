package com.crm.modules.telephony;

/**
 * Telephony boundary. The CRM core (calls, activities, analytics) depends ONLY on this
 * interface, so future providers (SIP, cloud telephony, cellular gateways) plug in without
 * touching Lead/Activity/Call/User modules. V1 ships exactly one implementation:
 * the Android cellular bridge (user's own phone + SIM).
 */
public interface TelephonyService {

    /** Stable provider id, persisted on call rows for traceability. */
    String providerId();

    /** False when this provider is not configured (e.g. no bridge URL set). */
    boolean available();

    /**
     * Ask the provider to place a cellular call from the given device to the given number.
     * Implementations MUST be side-effect free on failure (throwing = nothing was dialed)
     * and return the provider's call reference for later state callbacks.
     */
    InitiationResult initiate(CallCommand command) throws TelephonyException;

    /**
     * bridgeUrl: per-device bridge endpoint announced by the bridge heartbeat. Providers
     * should prefer it over any global default so each user's calls reach THEIR phone.
     */
    record CallCommand(UUID orgId, UUID userId, UUID deviceId, String deviceName,
                       String fromNumber, String toNumber, String customerName, String bridgeUrl) {}

    record InitiationResult(String providerRef, String state) {}

    class TelephonyException extends Exception {
        public TelephonyException(String message) { super(message); }
    }
}
