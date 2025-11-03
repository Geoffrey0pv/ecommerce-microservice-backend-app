package com.selimhorri.app.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

/**
 * Security configuration for staging environment.
 * Disables ALL security to allow E2E tests to run without JWT authentication.
 * 
 * This configuration is ONLY active when spring.profiles.active=staging
 * Production environments will use the default SecurityConfig with JWT.
 * 
 * IMPORTANT: This must be in the Docker image for staging deployment!
 */
@Configuration
@EnableWebSecurity
@Profile("staging")
public class NoSecurityConfig extends WebSecurityConfigurerAdapter {
	
	@Override
	protected void configure(final HttpSecurity http) throws Exception {
		http
			.cors().disable()
			.csrf().disable()
			.authorizeRequests()
				.anyRequest().permitAll()
			.and()
			.headers()
				.frameOptions()
				.sameOrigin();
	}
}
