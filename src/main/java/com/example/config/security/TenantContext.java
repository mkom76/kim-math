package com.example.config.security;

import com.example.entity.TeacherAcademyRole;

/**
 * Per-thread holder for the active tenant context (academy + role).
 *
 * <p>Populated by {@link SessionAuthenticationFilter} from the HTTP session
 * and consumed by {@link TenantFilterAspect} to enable Hibernate filters.
 * Cleared in the servlet filter's finally block to prevent leaks across
 * pooled threads.
 *
 * <p><b>Not propagated to async / scheduled threads.</b> Code paths under
 * {@code @Async} see {@link #current()} return null and run without tenant
 * filtering — they must enforce isolation explicitly.
 */
public final class TenantContext {

    public record Context(Long teacherId, Long academyId, TeacherAcademyRole role) {}

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long teacherId, Long academyId, TeacherAcademyRole role) {
        CONTEXT.set(new Context(teacherId, academyId, role));
    }

    /**
     * Set a student tenant context. Role is null to indicate student scope —
     * {@link TenantFilterAspect} will enable the academy filter but skip the
     * owner filter.
     */
    public static void setStudent(Long studentId, Long academyId) {
        CONTEXT.set(new Context(studentId, academyId, null));
    }

    public static Context current() {
        return CONTEXT.get();
    }

    public static boolean isPresent() {
        return CONTEXT.get() != null;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
