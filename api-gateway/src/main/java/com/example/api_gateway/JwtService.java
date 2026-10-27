package com.example.api_gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey chave;
    private final String emissor;
    private final long expiracaoEmMinutos;

    public JwtService(@Value("${jwt.secret}") String segredo,
            @Value("${jwt.issuer}") String emissor,
            @Value("${jwt.expiration-minutes}") long expiracaoEmMinutos) {
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.emissor = emissor;
        this.expiracaoEmMinutos = expiracaoEmMinutos;
    }

    public String gerarToken(String usuario, List<String> roles) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .issuer(emissor)
                .subject(usuario)
                .claim("roles", roles)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracaoEmMinutos, ChronoUnit.MINUTES)))
                .signWith(chave)
                .compact();
    }

    public Claims validar(String token) {
        return Jwts.parser()
                .verifyWith(chave)
                .requireIssuer(emissor)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}