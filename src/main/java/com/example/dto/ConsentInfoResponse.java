package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload shown on the public consent page before the parent signs.
 * No PII is exposed beyond what's needed to confirm identity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentInfoResponse {
    private String consentVersion;
    private String studentName;
    private String parentName;
    /** "010-****-1234" style mask so the parent can verify the link is for them. */
    private String parentPhoneMasked;
    private String academyName;
    private String className;
    /** Already-signed tokens still resolve but expose this flag so the UI can lock out. */
    private boolean alreadyConsented;
    /** True once token_expires_at has passed (UI shows a re-issue request hint). */
    private boolean expired;
}
