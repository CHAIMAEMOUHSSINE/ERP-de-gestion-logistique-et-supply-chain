package com.logismart.web;

import com.logismart.config.JwtService;
import com.logismart.domain.AppUser;
import com.logismart.dto.AuthDtos.*;
import com.logismart.repository.AppUserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final JwtService jwtService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        AppUser user = userRepository.findByUsername(request.username()).orElseThrow();
        JwtService.IssuedToken token = jwtService.generate(user.getUsername(), user.getRole().name());
        return new LoginResponse(token.value(), user.getUsername(), user.getFullName(),
                user.getRole().name(), token.expiresAt());
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        AppUser user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        return new MeResponse(user.getUsername(), user.getFullName(), user.getRole().name());
    }
}
