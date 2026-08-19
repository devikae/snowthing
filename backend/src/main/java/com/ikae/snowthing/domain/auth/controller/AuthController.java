package com.ikae.snowthing.domain.auth.controller;

import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<MemberLoginResponse> login(
            @Valid @RequestBody MemberLoginRequest loginRequest,
            HttpServletRequest request
    ) {
        MemberLoginResponse response = authService.login(loginRequest, request);
        return ResponseEntity.ok(response);
    }
}
