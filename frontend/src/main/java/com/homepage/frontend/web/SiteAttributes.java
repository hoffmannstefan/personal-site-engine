package com.homepage.frontend.web;

import com.homepage.frontend.content.ContentClient;
import com.homepage.frontend.content.Site;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.client.RestClientException;

/**
 * Makes the name, texts and start page of the site available to every template as {@code site}, including the error
 * pages, and turns a backend that is not available into a 503 page. While the backend is down, {@code site} is
 * {@link Site#UNAVAILABLE}, so the error page can still be shown.
 */
@ControllerAdvice(assignableTypes = {PageController.class, ErrorController.class})
class SiteAttributes {

    private static final Logger log = LoggerFactory.getLogger(SiteAttributes.class);

    private final ContentClient content;
    private final FrontendMetrics metrics;

    SiteAttributes(ContentClient content, FrontendMetrics metrics) {
        this.content = content;
        this.metrics = metrics;
    }

    @ModelAttribute("site")
    Site site() {
        try {
            return content.site();
        } catch (RestClientException e) {
            log.warn("Cannot read the site from the backend: {}", BackendFailure.describe(e));
            return Site.UNAVAILABLE;
        }
    }

    @ExceptionHandler(RestClientException.class)
    void backendUnavailable(RestClientException e, HttpServletResponse response) throws IOException {
        log.warn("Cannot read the page content from the backend: {}", BackendFailure.describe(e));
        metrics.backendUnavailable("page");
        response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value());
    }
}
