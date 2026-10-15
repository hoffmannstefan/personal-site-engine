package com.homepage.web;

import com.homepage.site.Site;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the name, texts and start page of the site available to every template as {@code site}, including the error pages. */
@ControllerAdvice
class SiteAttributes {

    private final Site site;

    SiteAttributes(Site site) {
        this.site = site;
    }

    @ModelAttribute("site")
    Site site() {
        return site;
    }
}
