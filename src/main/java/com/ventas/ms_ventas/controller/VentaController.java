package com.ventas.ms_ventas.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ventas.ms_ventas.exception.RecursoNoEncontradoException;
import com.ventas.ms_ventas.model.Venta;
import com.ventas.ms_ventas.service.VentaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {
    @Autowired
    private VentaService ventaService;

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

    @PostMapping
    public ResponseEntity<Venta> registrarVenta(@Valid @RequestBody Venta venta) {
        Venta nueva = ventaService.registrarVentaDirecta(venta);
        return new ResponseEntity<>(nueva, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Venta> getVenta(@PathVariable Long id) {
        Venta buscada = ventaService.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la venta con id " + id));
        return new ResponseEntity<>(buscada, HttpStatus.OK);
    }

    @GetMapping("/por-pedido/{idPedido}")
    public ResponseEntity<Venta> getVentaPorPedido(@PathVariable Long idPedido) {
        Venta buscada = ventaService.getVentaPorPedido(idPedido)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una venta asociada al pedido " + idPedido));
        return new ResponseEntity<>(buscada, HttpStatus.OK);
    }
}

