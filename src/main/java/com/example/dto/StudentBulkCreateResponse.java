package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of a bulk student import. Each item carries the new student's id +
 * the consent token that the admin should forward to the parent (via KakaoTalk
 * or SMS) so they can sign at {@code /consent/{token}}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentBulkCreateResponse {
    private List<Item> created;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Long studentId;
        private String name;
        private String parentName;
        private String parentPhone;
        private String consentToken;
    }
}
