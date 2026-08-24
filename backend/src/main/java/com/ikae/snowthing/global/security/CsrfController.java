package com.ikae.snowthing.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/csrf")
@RequiredArgsConstructor
public class CsrfController {

    @GetMapping
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
            "headerName", csrfToken.getHeaderName(),
            "parameterName", csrfToken.getParameterName(),
            "token", csrfToken.getToken()
        );
    }
}
