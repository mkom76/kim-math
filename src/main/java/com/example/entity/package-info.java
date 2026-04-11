/**
 * Tenant filter definitions shared by all academy-scoped entities.
 *
 * <p>{@code academyFilter} restricts queries to a single academy.
 * {@code ownerFilter} further restricts to classes owned by a specific teacher.
 *
 * <p>Filters are activated per-request by
 * {@link com.example.config.security.TenantFilterAspect}. Each entity
 * declares only the {@code @Filter} condition; the {@code @FilterDef}
 * lives here once to avoid Hibernate 6's "multiple FilterDef" error.
 */
@FilterDef(name = "academyFilter", parameters = @ParamDef(name = "academyId", type = Long.class))
@FilterDef(name = "ownerFilter", parameters = @ParamDef(name = "teacherId", type = Long.class))
package com.example.entity;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
