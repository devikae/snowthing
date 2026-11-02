package com.ikae.snowthing.global.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ikae.snowthing.domain.admin.service.VisitorStatsService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisitorTrackingInterceptor implements HandlerInterceptor {

    private final VisitorStatsService visitorStatsService;
    private final ClientIpResolver clientIpResolver;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/admin") || uri.startsWith("/api/admin")) {
            return true;
        }

        try {
            String clientIp = clientIpResolver.resolve(request);
            String userAgent = request.getHeader("User-Agent");

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isMember =
                    auth != null
                            && auth.isAuthenticated()
                            && auth.getPrincipal() instanceof CustomUserDetails;

            visitorStatsService.recordVisit(clientIp, userAgent, isMember);
        } catch (Exception e) {
            log.debug("Visitor tracking failed silently: {}", e.getMessage());
        }

        return true;
    }
}
