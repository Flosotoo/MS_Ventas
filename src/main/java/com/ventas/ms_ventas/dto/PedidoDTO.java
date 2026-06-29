package com.ventas.ms_ventas.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class PedidoDTO {
    private Long idPedido;
    private Long idCliente;
    private String estado;
    private BigDecimal total;   
}
