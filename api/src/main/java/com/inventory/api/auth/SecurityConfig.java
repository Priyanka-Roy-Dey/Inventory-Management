package com.inventory.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.inventory.api.user.Role;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security: every request outside {@link #PUBLIC_PATHS} needs a valid bearer token. Any role may
 * read; only Staff and Admin may write.
 */
@Configuration
public class SecurityConfig {

	private static final String[] PUBLIC_PATHS = { "/api/auth/login", "/api/auth/refresh", "/.well-known/jwks.json",
			"/actuator/health", "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
			"/v3/api-docs/**", "/error" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests.requestMatchers(PUBLIC_PATHS).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/**").authenticated()
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
		converter.setJwtGrantedAuthoritiesConverter(roles);
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
		writeError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
	}

	private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"message\":\"" + message + "\"}");
	}

}
