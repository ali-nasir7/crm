package com.crm.config;

import com.crm.security.CurrentUser;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Defense-in-depth tenant isolation: enables the Hibernate tenantFilter for every service call
 * executed within an authenticated request, so even an unscoped query can never cross organization
 * boundaries. Background workers (no security context) must scope explicitly by orgId — by design.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class TenantScopeAspect {

    public static final String FILTER = "tenantFilter";
    private final EntityManager entityManager;

    @Before("within(com.crm.modules..service..*)")
    public void enableTenantFilter() {
        UUID orgId = CurrentUser.orgIdOrNull();
        if (orgId != null) {
            Session session = entityManager.unwrap(Session.class);
            if (session.getEnabledFilter(FILTER) == null) {
                session.enableFilter(FILTER).setParameter("orgId", orgId.toString());
            }
        }
    }
}
