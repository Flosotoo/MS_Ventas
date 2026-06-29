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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/devoluciones")
public class DevolucionController {
    @Autowired
    private DevolucionService devolucionService;

    @PostMapping
    public ResponseEntity<Devolucion> procesarDevolucion(@Valid @RequestBody Devolucion devolucion) {
        Devolucion nueva = devolucionService.procesarDevolucion(devolucion);
        return new ResponseEntity<>(nueva, HttpStatus.CREATED);
    }

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

    @GetMapping("/{id}")
    public ResponseEntity<Devolucion> getDevolucion(@PathVariable Long id) {
        Devolucion buscada = devolucionService.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la devolución con id " + id));
        return new ResponseEntity<>(buscada, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> anularDevolucion(@PathVariable Long id) {
        devolucionService.anularDevolucion(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
