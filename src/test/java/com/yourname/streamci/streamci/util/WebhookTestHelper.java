package com.yourname.streamci.streamci.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * helper utilities for webhook testing
 * generates valid hmac signatures for test payloads
 */
public class WebhookTestHelper {

    /**
     * generate valid github webhook signature for testing
     * matches the signature format github uses: sha256=<hex>
     */
    public static String generateSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }

            return "sha256=" + hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("failed to generate signature", e);
        }
    }

    /**
     * default test secret - matches application-test.properties
     */
    public static final String TEST_SECRET = "test-webhook-secret-for-integration-testing";
}
