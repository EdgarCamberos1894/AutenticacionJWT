package com.cambers.auth.account.internal.config;

import com.cambers.auth.account.internal.password.FailClosedHaveIBeenPwnedPasswordChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class CompromisedPasswordConfig {

    private static final String HIBP_RANGE_API = "https://api.pwnedpasswords.com/range/";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    @Bean
    @Profile("prod")
    CompromisedPasswordChecker productionCompromisedPasswordChecker() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        RestClient restClient = RestClient.builder()
                .baseUrl(HIBP_RANGE_API)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "cambers-authentication-service")
                .build();

        return new FailClosedHaveIBeenPwnedPasswordChecker(restClient);
    }

    @Bean
    @Profile("!prod")
    CompromisedPasswordChecker nonProductionCompromisedPasswordChecker() {
        return password -> new CompromisedPasswordDecision(false);
    }
}
