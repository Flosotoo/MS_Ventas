package com.ventas.ms_ventas.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Entity
@Table(name = "venta")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Venta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVenta;

    //id externo de pedido para retiro con venta web
    @Positive(message = "El id de pedido debe ser un número positivo")
    @Column(name = "id_pedido", nullable = true)
    private Long idPedido;

    //id externo
    //En venta presencial anonima queda como null
    @Positive(message = "El id de cliente debe ser un número positivo")
    @Column(name = "id_cliente", nullable = true)
    private Long idCliente;

    @NotNull(message = "La sucursal es obligatoria")
    @Positive(message = "El id de sucursal debe ser un número positivo")
    @Column(name = "id_sucursal", nullable = false)
    private Long idSucursal;

    @Column(name = "subtotal_neto", nullable = false, precision = 10, scale = 0)
    private BigDecimal subtotalNeto;

    @PositiveOrZero(message = "El descuento no puede ser negativo")
    @Column(name = "porcentaje_descuento", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDescuento;

    @Column(nullable = false, precision = 10, scale = 0)
    private BigDecimal iva;

    @Column(nullable = false, precision = 10, scale = 0)
    private BigDecimal total;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Valid
    @NotEmpty(message = "La venta debe tener al menos un detalle")
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<DetalleVenta> detalles;
}
