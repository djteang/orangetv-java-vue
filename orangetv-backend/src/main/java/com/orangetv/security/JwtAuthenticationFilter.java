package com.orangetv.security;

import com.orangetv.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenService tokenService;

    @Value("${orangetv.internal-api-key:orangetv-internal-2024}")
    private String internalApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 1. 优先检查内部 API Key（前端 BFF 代理请求）
            if (authenticateByInternalKey(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 2. 常规 JWT 认证
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Long userId = tokenProvider.getUserIdFromToken(jwt);

                if (!tokenService.isTokenValid(userId, jwt)) {
                    log.debug("Token not found in Redis for userId: {}", userId);
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = tokenProvider.getUsernameFromToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 通过内部 API Key 认证（前端 BFF 代理请求）
     * 前端 api-proxy.ts 会发送 X-Internal-Api-Key、X-Auth-Username、X-Auth-Role 头
     */
    private boolean authenticateByInternalKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-Internal-Api-Key");
        if (!StringUtils.hasText(apiKey) || !apiKey.equals(internalApiKey)) {
            return false;
        }

        String username = request.getHeader("X-Auth-Username");
        String role = request.getHeader("X-Auth-Role");

        if (StringUtils.hasText(username)) {
            // 有用户信息，尝试从数据库加载完整用户
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                // 用户不在数据库中，用 header 中的角色信息构建临时认证
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (StringUtils.hasText(role)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                    if ("owner".equalsIgnoreCase(role)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }
                }
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        // 即使没有用户信息，内部 API Key 有效也返回 true（跳过 JWT 检查）
        return true;
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        // 1. 首先尝试从 Authorization header 获取
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // 2. 尝试从 Cookie 获取
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // 3. 尝试从查询参数获取
        String token = request.getParameter("token");
        if (StringUtils.hasText(token)) {
            return token;
        }

        return null;
    }
}
