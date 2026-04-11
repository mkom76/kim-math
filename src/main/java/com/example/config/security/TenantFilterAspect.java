package com.example.config.security;

import com.example.entity.TeacherAcademyRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Activates Hibernate filters for multi-tenant data isolation.
 *
 * <p>This aspect runs around methods annotated with @Transactional and enables
 * the {@code academyFilter} (and {@code ownerFilter} for non-admin teachers)
 * on the current Hibernate Session, scoped to the {@link TenantContext}.
 *
 * <h2>Load-bearing assumptions</h2>
 * <ul>
 *   <li><b>spring.jpa.open-in-view=true</b> (Spring Boot default). The filter
 *       activation relies on a request-bound Hibernate Session being shared
 *       between this aspect and the @Transactional method. If OSIV is disabled,
 *       this aspect MUST be replaced with a Session-creation hook or a
 *       HandlerInterceptor that activates filters at request-binding time.</li>
 *   <li><b>findById bypasses filters.</b> Hibernate filters apply to JPQL,
 *       Criteria, and HQL queries — but NOT to {@code EntityManager.find()} /
 *       {@code JpaRepository.findById()}. Any service that loads by primary key
 *       MUST also call {@code AuthorizationService} to verify ownership.</li>
 *   <li><b>Async / scheduled / non-HTTP threads have no TenantContext</b>, so
 *       this aspect no-ops silently. Code paths under {@code @Async} or
 *       {@code @Scheduled} are NOT tenant-isolated by this filter and must
 *       enforce isolation explicitly.</li>
 *   <li><b>TenantContext is treated as immutable per request.</b> Nested
 *       @Transactional invocations re-enable the same filter; the aspect does
 *       NOT save/restore prior parameters across nested calls.</li>
 * </ul>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) " +
            "|| @within(org.springframework.transaction.annotation.Transactional)")
    public Object enableFilters(ProceedingJoinPoint pjp) throws Throwable {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null) {
            return pjp.proceed();
        }

        Session session = entityManager.unwrap(Session.class);

        Filter academyFilter = session.enableFilter("academyFilter");
        academyFilter.setParameter("academyId", ctx.academyId());

        if (ctx.role() == TeacherAcademyRole.TEACHER) {
            Filter ownerFilter = session.enableFilter("ownerFilter");
            ownerFilter.setParameter("teacherId", ctx.teacherId());
        }

        return pjp.proceed();
    }
}
