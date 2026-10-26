package com.homepage.frontend.content;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class BackendConfig {

    /** The client for the backend, with the timeouts {@code spring.http.client.*}. */
    @Bean
    RestClient backend(RestClient.Builder builder, BackendProperties properties) {
        return builder.baseUrl(properties.backendUrl()).build();
    }
}
