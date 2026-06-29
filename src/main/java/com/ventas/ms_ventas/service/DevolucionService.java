package com.ventas.ms_ventas.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.ventas.ms_ventas.dto.AjusteStockDTO;
import com.ventas.ms_ventas.exception.DevolucionInvalidaException;
import com.ventas.ms_ventas.exception.RecursoNoEncontradoException;
import com.ventas.ms_ventas.model.DetalleDevolucion;
import com.ventas.ms_ventas.model.DetalleVenta;
import com.ventas.ms_ventas.model.Devolucion;
import com.ventas.ms_ventas.model.Venta;
import com.ventas.ms_ventas.repository.DevolucionRepository;
import com.ventas.ms_ventas.repository.VentaRepository;

@Service
public class DevolucionService {
    @Autowired
    private DevolucionRepository devolucionRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${ms.inventario.ajuste.url}")
    private String URL_MS_INVENTARIO_AJUSTE;

    public Devolucion procesarDevolucion(Devolucion devolucion) {
        Long idVenta = devolucion.getVenta().getIdVenta();
        Venta venta = ventaRepository.findById(idVenta)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la venta con id " + idVenta));
        devolucion.setVenta(venta);
        Map<Long, Integer> vendidoPorProducto = new HashMap<>();
        for (DetalleVenta dv : venta.getDetalles()) {
            vendidoPorProducto.merge(dv.getIdProducto(), dv.getCantidad(), Integer::sum);
        }
        Map<Long, Integer> yaDevuelto = new HashMap<>();
        for (Devolucion devPrevia : devolucionRepository.findByVenta_IdVenta(idVenta)) {
            for (DetalleDevolucion dd : devPrevia.getDetalles()) {
                yaDevuelto.merge(dd.getIdProducto(), dd.getCantidad(), Integer::sum);
            }
        }
        for (DetalleDevolucion dd : devolucion.getDetalles()) {
            Long idProd = dd.getIdProducto();
            int vendido = vendidoPorProducto.getOrDefault(idProd, 0);
            if (vendido == 0) {
                throw new DevolucionInvalidaException(
                        "El producto " + idProd + " no pertenece a la venta " + idVenta);
            }
            int devueltoAntes = yaDevuelto.getOrDefault(idProd, 0);
            int disponibleParaDevolver = vendido - devueltoAntes;
            if (dd.getCantidad() > disponibleParaDevolver) {
                throw new DevolucionInvalidaException(
                        "No se puede devolver " + dd.getCantidad() + " del producto " + idProd
                                + ": se vendieron " + vendido + " y ya se devolvieron " + devueltoAntes
                                + " (quedan " + disponibleParaDevolver + ")");
            }
            dd.setDevolucion(devolucion);
        }
        devolucion.setFecha(LocalDateTime.now());
        Devolucion guardada = devolucionRepository.save(devolucion);
        // reingreso a stock físico
        for (DetalleDevolucion dd : guardada.getDetalles()) {
            AjusteStockDTO ajuste = new AjusteStockDTO(
                    dd.getIdProducto(),
                    venta.getIdSucursal(),
                    dd.getCantidad(), // POSITIVO: la devolución reingresa stock
                    "devolucion-" + guardada.getIdDevolucion() + "-producto-" + dd.getIdProducto());
            restTemplate.put(URL_MS_INVENTARIO_AJUSTE, ajuste);
        }
        return guardada;
    }

    public Optional<Devolucion> findById(Long id) {
        return devolucionRepository.findById(id);
    }

    public List<Devolucion> listarDevoluciones() {
        return devolucionRepository.findAll();
    }

    public List<Devolucion> listarPorVenta(Long idVenta) {
        return devolucionRepository.findByVenta_IdVenta(idVenta);
    }

    public void anularDevolucion(Long id) {
        Devolucion devolucion = devolucionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la devolución con id " + id));
        Long idSucursal = devolucion.getVenta().getIdSucursal();
        // se revierte la devolución. idOperacion con prefijo distinto para que la
        // idempotencia no lo confunda.
        for (DetalleDevolucion dd : devolucion.getDetalles()) {
            AjusteStockDTO ajuste = new AjusteStockDTO(
                    dd.getIdProducto(),
                    idSucursal,
                    -dd.getCantidad(), // NEGATIVO: deshace el reingreso
                    "anulacion-devolucion-" + devolucion.getIdDevolucion() + "-producto-" + dd.getIdProducto());
            restTemplate.put(URL_MS_INVENTARIO_AJUSTE, ajuste);
        }
        devolucionRepository.delete(devolucion);
    }
}
