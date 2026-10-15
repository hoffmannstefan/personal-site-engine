package com.homepage.comment;

import com.homepage.site.SiteProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CommentConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RateLimiter commentRateLimiter(SiteProperties site, Clock clock) {
        return new RateLimiter(site.comments().maxPerMinute(), clock);
    }
}
