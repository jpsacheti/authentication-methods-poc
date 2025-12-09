package dev.jpsacheti.authpoc.config;

import dev.jpsacheti.authpoc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the authentication POC.
 *
 * <h2>CSRF Protection</h2>
 * <p>
 * CSRF (Cross-Site Request Forgery) protection is <b>intentionally disabled</b>
 * in this configuration
 * because this API uses <b>stateless JWT authentication</b>.
 * </p>
 *
 * <h3>Why CSRF is disabled:</h3>
 * <ul>
 * <li>CSRF attacks exploit the browser's automatic cookie submission
 * behavior</li>
 * <li>This API uses JWT tokens transmitted via the {@code Authorization}
 * header, not cookies</li>
 * <li>Since browsers don't automatically attach custom headers to cross-origin
 * requests,
 * CSRF attacks are not possible against token-based stateless APIs</li>
 * <li>The session policy is set to {@code STATELESS}, meaning no HTTP session
 * is created</li>
 * </ul>
 *
 * <h3>When to enable CSRF protection:</h3>
 * <p>
 * If you modify this application to use:
 * </p>
 * <ul>
 * <li>Session-based authentication (cookies)</li>
 * <li>JWT tokens stored in cookies (especially without {@code SameSite}
 * attribute)</li>
 * <li>Form-based login with server-side sessions</li>
 * </ul>
 *
 * <h3>How to enable token-based CSRF protection:</h3>
 * <p>
 * Replace {@code .csrf(AbstractHttpConfigurer::disable)} with:
 * </p>
 * 
 * <pre>{@code
 * .csrf(csrf -> csrf
 *     .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
 *     .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
 *     .ignoringRequestMatchers("/auth/login", "/auth/register") // Exclude public endpoints
 * )
 * }</pre>
 *
 * <p>
 * For SPA (Single Page Applications), add a filter to expose the token:
 * </p>
 * 
 * <pre>{@code
 * .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
 *
 * // Where CsrfCookieFilter is:
 * public class CsrfCookieFilter extends OncePerRequestFilter {
 *     &#64;Override
 *     protected void doFilterInternal(HttpServletRequest request,
 *             HttpServletResponse response, FilterChain filterChain)
 *             throws ServletException, IOException {
 *         CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
 *         if (csrfToken != null) {
 *             csrfToken.getToken(); // Forces token generation
 *         }
 *         filterChain.doFilter(request, response);
 *     }
 * }
 * }</pre>
 *
 * <p>
 * The client must then include the CSRF token in requests:
 * </p>
 * <ul>
 * <li>Read the {@code XSRF-TOKEN} cookie</li>
 * <li>Include it as the {@code X-XSRF-TOKEN} header in state-changing requests
 * (POST, PUT, DELETE)</li>
 * </ul>
 *
 * @see <a href=
 *      "https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html">
 *      Spring Security CSRF Documentation</a>
 * @see org.springframework.security.web.csrf.CookieCsrfTokenRepository
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserRepository userRepository;

    /**
     * Configures the security filter chain.
     * <p>
     * CSRF is disabled because this API uses stateless JWT authentication.
     * See class-level Javadoc for details and instructions on enabling CSRF if
     * needed.
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF disabled: Safe for stateless JWT APIs. See class Javadoc for
                // re-enabling.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/webauthn/**", "/h2-console/**", "/", "/index.html", "/app.js",
                                "/style.css", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                // Allow H2 console frames
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
