package com.crm.modules.email.web;

import com.crm.modules.email.service.EmailService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public tracking endpoints (allowed anonymously by SecurityConfig). */
@RestController
@RequestMapping("/api/v1/track")
@RequiredArgsConstructor
@Tag(name = "Email Tracking")
public class TrackingController {

    private static final byte[] PIXEL = new byte[]{
        71, 73, 70, 56, 57, 97, 1, 0, 1, 0, -128, 0, 0, 0, 0, 0, -1, -1, -1, 44, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 68, 1, 0, 59};

    private final EmailService emailService;

    @GetMapping("/open/{trackingId}")
    public ResponseEntity<byte[]> open(@PathVariable String trackingId) {
        try {
            emailService.recordOpen(trackingId);
        } catch (Exception ignored) {
            // tracking must never fail the pixel
        }
        return ResponseEntity.ok().contentType(MediaType.valueOf("image/gif")).body(PIXEL);
    }

    /** Provider webhook receiver for delivery/bounce/complaint events.
     *  TODO / Integration Required: verify provider signatures (SES SNS, Postmark, etc.) before enabling in production. */
    @PostMapping("/events")
    public ResponseEntity<Void> events(@RequestBody String payload) {
        return ResponseEntity.accepted().build();
    }
}
