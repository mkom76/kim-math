package com.example.config.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fails startup loudly if {@code spring.jpa.open-in-view} is disabled.
 * The {@link TenantFilterAspect} relies on a request-bound Hibernate Session
 * being shared with @Transactional methods, which OSIV provides. If OSIV is
 * off, multi-tenant filters silently fail to apply, which is a security risk.
 * Bypass only after replacing TenantFilterAspect with a session-binding hook.
 */
@Component
public class TenantFilterStartupCheck {

    @Value("${spring.jpa.open-in-view:true}")
    private boolean openInView;

    @PostConstruct
    void verify() {
        if (!openInView) {
            throw new IllegalStateException(
                "spring.jpa.open-in-view is false, but TenantFilterAspect requires " +
                "a request-bound Hibernate Session. Either re-enable OSIV or replace " +
                "TenantFilterAspect with a session-binding mechanism. Refusing to start " +
                "to prevent silent tenant isolation bypass."
            );
        }
    }
}
