package com.ventas.ms_ventas.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class PedidoDTO {
    private Long idPedido;
    private Long idCliente;
    private String estado;
    private String tipoEntrega;
    private Long idSucursalRetiro;
    private BigDecimal total;   
    private List<DetallePedidoDTO> detalles;

    @Data
    public static class DetallePedidoDTO {
        private Long idProducto;
        private Integer cantidad;
        private BigDecimal precioUnitario;
    }
    
}
