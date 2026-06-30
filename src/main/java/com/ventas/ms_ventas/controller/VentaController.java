package com.ventas.ms_ventas.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ventas.ms_ventas.exception.RecursoNoEncontradoException;
import com.ventas.ms_ventas.model.Venta;
import com.ventas.ms_ventas.service.VentaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ventas")
@Tag(name = "Ventas", description = "Gestión de ventas presenciales y retiros web (HU-26, HU-28, HU-55)")

public class VentaController {
    @Autowired
    private VentaService ventaService;

    @Operation(summary = "Listar ventas", description = "Lista todas las ventas, o filtra por sucursal con el parámetro idSucursal. 204 si no hay resultados.")
    @GetMapping
    public ResponseEntity<List<Venta>> getVentas(@RequestParam(required = false) Long idSucursal) {
        List<Venta> ventas = (idSucursal != null)
                ? ventaService.listarPorSucursal(idSucursal)
                : ventaService.listarVentas();
        if (ventas.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(ventas, HttpStatus.OK);
    }

    @Operation(summary = "Registrar venta presencial", description = "HU-26: registra una venta directa. Valida stock de todos los productos antes de descontar, calcula IVA 19% y total. 409 si el descuento supera 50% o si falta stock.")
    @PostMapping
    public ResponseEntity<Venta> registrarVenta(@Valid @RequestBody Venta venta) {
        Venta nueva = ventaService.registrarVentaDirecta(venta);
        return new ResponseEntity<>(nueva, HttpStatus.CREATED);
    }

    @Operation(summary = "Registrar retiro de pedido web", description = "HU-55: registra la venta de un pedido web al momento del retiro en tienda. Requiere idPedido y marca el pedido como RETIRADO en MS Envíos.")
    @PostMapping("/retiro")
    public ResponseEntity<Venta> registrarRetiro(@Valid @RequestBody Venta venta) {
        Venta nueva = ventaService.registrarRetiro(venta);
        return new ResponseEntity<>(nueva, HttpStatus.CREATED);
    }

    @Operation(summary = "Obtener venta por id", description = "Devuelve una venta con sus detalles. 404 si no existe.")
    @GetMapping("/{id}")
    public ResponseEntity<Venta> getVenta(@PathVariable Long id) {
        Venta buscada = ventaService.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la venta con id " + id));
        return new ResponseEntity<>(buscada, HttpStatus.OK);
    }

    @Operation(summary = "Obtener venta por pedido", description = "Busca la venta asociada a un pedido web. 404 si no existe venta para ese pedido.")
    @GetMapping("/por-pedido/{idPedido}")
    public ResponseEntity<Venta> getVentaPorPedido(@PathVariable Long idPedido) {
        Venta buscada = ventaService.getVentaPorPedido(idPedido)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una venta asociada al pedido " + idPedido));
        return new ResponseEntity<>(buscada, HttpStatus.OK);
    }

    @Operation(summary = "Anular venta", description = "Anula una venta y reingresa el stock vendido al inventario. 404 si no existe.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> anularVenta(@PathVariable Long id) {
        ventaService.anularVenta(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Actualizar descuento", description = "HU-28: cambia el porcentaje de descuento de una venta y recalcula IVA y total. 409 si supera el 50% sin autorización.")
    @PutMapping("/{id}/descuento")
    public ResponseEntity<Venta> actualizarDescuento(
            @PathVariable Long id,
            @RequestParam BigDecimal porcentaje) {
        Venta actualizada = ventaService.actualizarDescuento(id, porcentaje);
        return new ResponseEntity<>(actualizada, HttpStatus.OK);
    }
}
