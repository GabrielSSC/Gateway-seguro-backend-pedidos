package com.example.api_gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
public class ProxyController {

    private final RestClient restClient;

    public ProxyController(@Value("${backend.url}") String backendUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(backendUrl)
                .build();
    }

    @GetMapping("/api/**")
    public ResponseEntity<?> encaminhar(HttpServletRequest request) {
        String destino = request.getRequestURI();
        if (request.getQueryString() != null) {
            destino += "?" + request.getQueryString();
        }

        ResponseEntity<byte[]> resposta;
        try {
            resposta = restClient.get()
                    .uri(destino)
                    .header("X-Gateway-Authenticated", "true")
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiErrorResponse.of(HttpStatus.BAD_GATEWAY.value(),
                            HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                            "Servico de pedidos indisponivel.", request.getRequestURI()));
        }

        MediaType contentType = resposta.getHeaders().getContentType();
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.status(resposta.getStatusCode())
                .contentType(contentType)
                .body(resposta.getBody());
    }
}
