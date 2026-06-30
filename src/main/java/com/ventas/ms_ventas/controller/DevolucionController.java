package com.ventas.ms_ventas.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ventas.ms_ventas.exception.RecursoNoEncontradoException;
import com.ventas.ms_ventas.model.Devolucion;
import com.ventas.ms_ventas.service.DevolucionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/devoluciones")
@Tag(name = "Devoluciones", description = "Gestión de devoluciones de productos vendidos (HU-40)")
public class DevolucionController {
    @Autowired
    private DevolucionService devolucionService;

    @Operation(summary = "Procesar devolución", description = "HU-40: registra una devolución sobre una venta existente y reingresa el stock devuelto. Valida que no se devuelva más de lo vendido (considerando devoluciones previas). 409 si la cantidad es inválida.")
    @PostMapping
    public ResponseEntity<Devolucion> procesarDevolucion(@Valid @RequestBody Devolucion devolucion) {
        Devolucion nueva = devolucionService.procesarDevolucion(devolucion);
        return new ResponseEntity<>(nueva, HttpStatus.CREATED);
    }

    @Operation(summary = "Listar devoluciones", description = "Lista todas las devoluciones, o filtra por venta con el parámetro idVenta. 204 si no hay resultados.")
    @GetMapping
    public ResponseEntity<List<Devolucion>> getDevoluciones(@RequestParam(required = false) Long idVenta) {
        List<Devolucion> devoluciones = (idVenta != null)
                ? devolucionService.listarPorVenta(idVenta)
                : devolucionService.listarDevoluciones();
        if (devoluciones.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(devoluciones, HttpStatus.OK);
    }

    @Operation(summary = "Obtener devolución por id", description = "Devuelve una devolución con sus detalles. 404 si no existe.")
    @GetMapping("/{id}")
    public ResponseEntity<Devolucion> getDevolucion(@PathVariable Long id) {
        Devolucion buscada = devolucionService.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la devolución con id " + id));
        return new ResponseEntity<>(buscada, HttpStatus.OK);
    }

    @Operation(summary = "Anular devolución", description = "Anula una devolución y revierte el reingreso de stock que había generado. 404 si no existe.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> anularDevolucion(@PathVariable Long id) {
        devolucionService.anularDevolucion(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
