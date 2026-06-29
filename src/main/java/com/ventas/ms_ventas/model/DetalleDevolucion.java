package com.ventas.ms_ventas.model;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "detalle_devolucion")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleDevolucion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDetalleDevolucion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_devolucion", nullable = false, foreignKey = @ForeignKey(name = "fk_detalle_devolucion"))
    @JsonBackReference
    private Devolucion devolucion;

    @NotNull(message = "El producto es obligatorio")
    @Positive(message = "El id de producto debe ser un número positivo")
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    @NotNull(message = "La cantidad es obligatoria")
    @Positive(message = "La cantidad debe ser un número positivo")
    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;
}
