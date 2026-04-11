package com.example.config.security;

import com.example.entity.TeacherAcademyRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class SessionAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Long userId = (Long) session.getAttribute("userId");
                String userRole = (String) session.getAttribute("userRole");

                if (userId != null && userRole != null) {
                    String effectiveRole = userRole;

                    if ("TEACHER".equals(userRole)) {
                        Long activeAcademyId = (Long) session.getAttribute("activeAcademyId");
                        String activeRoleStr = (String) session.getAttribute("activeRole");
                        if (activeAcademyId != null && activeRoleStr != null) {
                            TeacherAcademyRole activeRole = TeacherAcademyRole.valueOf(activeRoleStr);
                            TenantContext.set(userId, activeAcademyId, activeRole);
                            effectiveRole = activeRoleStr;
                        }
                    } else if ("STUDENT".equals(userRole)) {
                        Long studentAcademyId = (Long) session.getAttribute("studentAcademyId");
                        if (studentAcademyId != null) {
                            TenantContext.setStudent(userId, studentAcademyId);
                        }
                    }

                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + effectiveRole);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, List.of(authority));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
