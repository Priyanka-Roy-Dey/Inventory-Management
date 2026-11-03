package com.inventory.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.inventory.api.user.Role;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security: every request outside {@link #PUBLIC_PATHS} needs a valid bearer token. User management
 * is Admin only; otherwise any role may read and only Staff and Admin may write. A user who must change their
 * password gets no role, so they can only call {@code /api/auth/change-password}.
 */
@Configuration
public class SecurityConfig {

	static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

	private static final String[] PUBLIC_PATHS = { "/api/auth/login", "/api/auth/refresh", "/.well-known/jwks.json",
			"/actuator/health", "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
			"/v3/api-docs/**", "/error" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests.requestMatchers(PUBLIC_PATHS).permitAll()
				.requestMatchers("/api/auth/change-password").authenticated()
				.requestMatchers("/api/users", "/api/users/**").hasRole(Role.ADMIN.name())
				.requestMatchers(HttpMethod.GET, "/api/**")
				.hasAnyRole(Role.ADMIN.name(), Role.STAFF.name(), Role.VIEWER.name())
				.requestMatchers("/api/**").hasAnyRole(Role.STAFF.name(), Role.ADMIN.name())
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer(resourceServer -> resourceServer
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
				.authenticationEntryPoint(SecurityConfig::unauthorized)
				.accessDeniedHandler(SecurityConfig::forbidden))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(SecurityConfig::unauthorized)
				.accessDeniedHandler(SecurityConfig::forbidden));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName("role");
		roles.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> Boolean.TRUE
			.equals(jwt.getClaimAsBoolean(TokenService.MUST_CHANGE_PASSWORD_CLAIM))
					? List.of(new SimpleGrantedAuthority(PASSWORD_CHANGE_REQUIRED)) : roles.convert(jwt));
		return converter;
	}

	private static void unauthorized(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException ex) throws IOException {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		String message = ex instanceof InvalidBearerTokenException ? "Invalid or expired token"
				: "Authentication required";
		writeError(response, HttpServletResponse.SC_UNAUTHORIZED, message);
	}

	private static void forbidden(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException ex) throws IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		boolean mustChangePassword = authentication != null && authentication.getAuthorities()
			.stream()
			.anyMatch(authority -> PASSWORD_CHANGE_REQUIRED.equals(authority.getAuthority()));
		writeError(response, HttpServletResponse.SC_FORBIDDEN,
				mustChangePassword ? "Password change required" : "Access denied");
	}

	private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"message\":\"" + message + "\"}");
	}

}
