package com.acme.salary.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * When the app is deployed, Spring serves the Angular files itself (one address for page and API, which the
 * cookies and CSRF protection rely on). Angular handles pages like /insights in the browser, so if someone
 * reloads the page or opens such a link directly, the server must answer with index.html and let Angular take over.
 * Add a path here when you add a top-level page to the Angular routes.
 */
@Controller
class SpaForwardController {

    @GetMapping({"/sign-in", "/access", "/insights"})
    String forwardToAngular() {
        return "forward:/index.html";
    }
}
