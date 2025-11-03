package com.selimhorri.app.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.selimhorri.app.business.user.model.RoleBasedAuthority;
import com.selimhorri.app.config.filter.JwtRequestFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {
	
	private final UserDetailsService userDetailsService;
	private final PasswordEncoder passwordEncoder;
	private final JwtRequestFilter jwtRequestFilter;
	
	@Override
	protected void configure(final AuthenticationManagerBuilder auth) throws Exception {
		auth.userDetailsService(this.userDetailsService)
			.passwordEncoder(this.passwordEncoder);
	}
	
	@Override
	protected void configure(final HttpSecurity http) throws Exception {
		http.cors().disable()
			.csrf().disable()
			.authorizeRequests()
				.antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.antMatchers("/", "index", "**/css/**", "**/js/**").permitAll()
				// Authentication endpoints - permitAll
				.antMatchers("/api/authenticate/**").permitAll()
				.antMatchers("/app/api/authenticate/**").permitAll()
				// Public read endpoints - permitAll
				.antMatchers("/api/categories/**").permitAll()
				.antMatchers("/app/product-service/api/categories/**").permitAll()
				.antMatchers(HttpMethod.GET, "/api/products/**").permitAll()
				.antMatchers(HttpMethod.GET, "/app/product-service/api/products/**").permitAll()
				// User registration - permitAll (for E2E tests to create test users)
				.antMatchers(HttpMethod.POST, "/api/users").permitAll()
				.antMatchers(HttpMethod.POST, "/app/user-service/api/users").permitAll()
				// Actuator health endpoints - permitAll
				.antMatchers("/actuator/health/**", "/actuator/info/**").permitAll()
				.antMatchers("/app/actuator/health/**", "/app/actuator/info/**").permitAll()
				// Admin actuator endpoints - ROLE_ADMIN only
				.antMatchers("/actuator/**", "/app/actuator/**")
					.hasAnyRole(RoleBasedAuthority.ROLE_ADMIN.getRole())
				// All other /api/** endpoints require authentication
				.antMatchers("/api/**", "/app/*-service/api/**")
					.hasAnyRole(RoleBasedAuthority.ROLE_USER.getRole(), 
							RoleBasedAuthority.ROLE_ADMIN.getRole())
				.anyRequest().authenticated()
			.and()
			.headers()
				.frameOptions()
				.sameOrigin()
			.and()
			.sessionManagement()
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
			.and()
			.addFilterBefore(this.jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
	}
	
	@Bean
	@Override
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}
	
	
	
}










