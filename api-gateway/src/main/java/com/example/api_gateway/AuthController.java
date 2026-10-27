package com.example.api_gateway;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    public record LoginRequest(String username, String password) {
    }

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        List<String> roles = null;

        if ("admin".equals(request.username()) && "admin123".equals(request.password())) {
            roles = List.of("ADMIN", "USER");
        } else if ("user".equals(request.username()) && "user123".equals(request.password())) {
            roles = List.of("USER");
        }

        if (roles == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Credenciais inválidas."));
        }

        String token = jwtService.gerarToken(request.username(), roles);
        return ResponseEntity.ok(Map.of("token", token));
    }
}
