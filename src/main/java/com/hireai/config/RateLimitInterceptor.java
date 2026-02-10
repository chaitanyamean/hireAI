package com.hireai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireai.domain.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int GENERAL_LIMIT = 60;
    private static final int AI_LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final Set<String> AI_PATHS = Set.of(
            "/api/v1/match", "/api/v1/interviews"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientId = getClientIdentifier(request);
        String path = request.getRequestURI();

        boolean isAiEndpoint = AI_PATHS.stream().anyMatch(path::startsWith);
        int limit = isAiEndpoint ? AI_LIMIT : GENERAL_LIMIT;
        String bucketKey = "ratelimit:" + (isAiEndpoint ? "ai:" : "general:") + clientId;

        Long count = redisTemplate.opsForValue().increment(bucketKey);
        if (count != null && count == 1) {
            redisTemplate.expire(bucketKey, WINDOW);
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - (count != null ? count : 0))));

        if (count != null && count > limit) {
            log.warn("Rate limit exceeded: client={}, path={}, count={}", clientId, path, count);
            Long ttl = redisTemplate.getExpire(bucketKey);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(ttl != null ? ttl : 60));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error("Rate limit exceeded. Try again in " + (ttl != null ? ttl : 60) + " seconds.")));
            return false;
        }

        return true;
    }

    private String getClientIdentifier(HttpServletRequest request) {
        // Use authenticated user email if available, otherwise use IP
        String user = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null;
        if (user != null) return user;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
