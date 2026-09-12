package com.crm.common.api;

import java.time.Instant;

public record ErrorResponse(String code, String message, Object details, String traceId, Instant timestamp) {}
