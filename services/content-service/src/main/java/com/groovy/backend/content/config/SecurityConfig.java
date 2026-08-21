package com.groovy.backend.content.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.groovy.backend.common.auth.JwtAuthenticationEntryPoint;
import com.groovy.backend.security.JwtAuthenticationFilter;
import com.groovy.backend.security.TokenProvider;

import lombok.RequiredArgsConstructor;

/**
 * MSA 전환(content-service 추출): groovy(레거시) SecurityConfig와 동일한 permitAll 규칙을
 * 그대로 옮긴다 — 회고록 목록/상세/댓글 목록은 비로그인 사용자도 조회 가능해야 한다(liked
 * 여부만 로그인 사용자마다 달라짐). "/api/memoirs/my-studies", "/api/memoirs/mine"은 단일
 * 세그먼트 와일드카드("/api/memoirs/{memoirId}")보다 먼저 명시해야 로그인 요구가 적용된다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private static final String[] PERMIT_ALL_PATTERNS = {
		"/actuator/health",
		"/actuator/prometheus"
	};

	private static final String[] PERMIT_ALL_GET_PATTERNS = {
		"/api/memoirs",
		"/api/memoirs/{memoirId}",
		"/api/memoirs/{memoirId}/comments"
	};

	private final TokenProvider tokenProvider;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/memoirs/my-studies").authenticated()
				.requestMatchers(HttpMethod.GET, "/api/memoirs/mine").authenticated()
				.requestMatchers(HttpMethod.GET, PERMIT_ALL_GET_PATTERNS).permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(handler -> handler.authenticationEntryPoint(jwtAuthenticationEntryPoint))
			// 회고록 목록/상세는 비회원도 보되 좋아요 여부는 로그인 사용자마다 달라야 해서, 토큰이 없을 때
			// @AuthenticationPrincipal이 스프링 시큐리티 기본 익명 principal로 채워지지 않고 null이
			// 되도록 익명 인증 필터를 끈다. permitAll 자체는 인증 객체 유무와 무관하게 동작한다.
			.anonymous(AbstractHttpConfigurer::disable)
			.addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
