package com.ventas.ms_ventas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AjusteStockDTO {
    private Long idProducto;
    private Long idSucursal;
    private int cantidad;
    private String idOperacion; //idempotencia
}
