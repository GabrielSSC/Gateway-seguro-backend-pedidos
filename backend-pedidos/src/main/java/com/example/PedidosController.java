package com.example;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PedidosController {

    public record Pedido(int id, String produto, double valor) {
    }

    @GetMapping("/api/pedidos")
    public List<Pedido> listar() {
        return List.of(
                new Pedido(1, "Notebook", 3500.00),
                new Pedido(2, "Mouse", 80.00));
    }

    @GetMapping("/api/admin/relatorio")
    public Map<String, Object> relatorio() {
        return Map.of("totalPedidos", 2, "valorTotal", 3580.00);
    }
}