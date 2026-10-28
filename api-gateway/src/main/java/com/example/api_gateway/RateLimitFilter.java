package com.example.api_gateway;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final long JANELA_MS = 60_000;
    private static final int LIMITE_PARA_LIMPEZA = 10_000;

    private record Janela(long inicio, int contador) {
    }

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private final int maxRequisicoes;
    private final ApiErrorResponseWriter errorResponseWriter;

    public RateLimitFilter(int maxRequisicoes, ApiErrorResponseWriter errorResponseWriter) {
        this.maxRequisicoes = maxRequisicoes;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        long agora = System.currentTimeMillis();
        String chave = identificarCliente(request);

        Janela atual = janelas.compute(chave, (k, janela) -> {
            if (janela == null || agora - janela.inicio() >= JANELA_MS) {
                return new Janela(agora, 1);
            }
            return new Janela(janela.inicio(), janela.contador() + 1);
        });

        limparJanelasAntigas(agora);

        if (atual.contador() > maxRequisicoes) {
            long segundos = (atual.inicio() + JANELA_MS - agora) / 1000 + 1;
            response.setHeader("Retry-After", String.valueOf(segundos));
            errorResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                    "Limite de requisicoes excedido. Tente novamente mais tarde.",
                    request.getRequestURI());
            return;
        }

        chain.doFilter(request, response);
    }

    private String identificarCliente(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return "user:" + auth.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void limparJanelasAntigas(long agora) {
        if (janelas.size() > LIMITE_PARA_LIMPEZA) {
            janelas.entrySet().removeIf(e -> agora - e.getValue().inicio() >= JANELA_MS);
        }
    }
}