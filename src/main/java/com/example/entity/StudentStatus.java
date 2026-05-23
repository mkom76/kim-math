package com.example.entity;

/**
 * Lifecycle state of a student account.
 *
 * <ul>
 *   <li>{@code PENDING_CONSENT} — created via bulk import; cannot log in until
 *       guardian completes the online consent flow.</li>
 *   <li>{@code ACTIVE} — consent recorded (online or legacy offline); login OK.</li>
 *   <li>{@code REVOKED} — guardian withdrew consent; account frozen.</li>
 * </ul>
 */
public enum StudentStatus {
    PENDING_CONSENT,
    ACTIVE,
    REVOKED
}
