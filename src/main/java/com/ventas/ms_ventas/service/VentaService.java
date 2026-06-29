package com.ventas.ms_ventas.service;

import java.math.BigDecimal;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.ventas.ms_ventas.dto.AjusteStockDTO;
import com.ventas.ms_ventas.dto.ProductoDTO;
import com.ventas.ms_ventas.exception.DescuentoNoAutorizadoException;
import com.ventas.ms_ventas.exception.RecursoNoEncontradoException;
import com.ventas.ms_ventas.exception.StockInsuficienteException;
import com.ventas.ms_ventas.model.DetalleVenta;
import com.ventas.ms_ventas.model.Venta;
import com.ventas.ms_ventas.repository.VentaRepository;

@Service
public class VentaService {
    private static final Logger log = LoggerFactory.getLogger(VentaService.class);

    // Tope de descuento sin autorizacion
    private static final BigDecimal DESCUENTO_MAXIMO = new BigDecimal("50");

    // IVA
    private static final BigDecimal TASA_IVA = new BigDecimal("0.19");

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${ms.productos.url}")
    private String URL_MS_PRODUCTOS;

    @Value("${ms.inventario.ajuste.url}")
    private String URL_MS_INVENTARIO_AJUSTE;

    @Value("${ms.inventario.disponibilidad.url}")
    private String URL_MS_INVENTARIO_DISPONIBILIDAD;

    public Venta registrarVentaDirecta(Venta venta) {
        BigDecimal descuento = (venta.getPorcentajeDescuento() != null)
                ? venta.getPorcentajeDescuento()
                : BigDecimal.ZERO;
        if (descuento.compareTo(DESCUENTO_MAXIMO) > 0) {
            throw new DescuentoNoAutorizadoException(
                    "El descuento de " + descuento + "% supera el máximo permitido ("
                            + DESCUENTO_MAXIMO + "%) sin autorización de gerente");
        }
        venta.setPorcentajeDescuento(descuento);
        // Validacion de cada producto y calculo de subtotales
        BigDecimal subtotalNeto = BigDecimal.ZERO;
        for (DetalleVenta detalle : venta.getDetalles()) {
            String url = URL_MS_PRODUCTOS + detalle.getIdProducto();
            ProductoDTO producto = restTemplate.getForObject(url, ProductoDTO.class);
            if (producto == null) {
                throw new RecursoNoEncontradoException(
                        "El producto " + detalle.getIdProducto() + " no existe en el catálogo");
            }
            detalle.setVenta(venta);
            BigDecimal subtotal = detalle.getPrecioUnitario()
                    .multiply(BigDecimal.valueOf(detalle.getCantidad()));
            detalle.setSubtotal(subtotal);
            detalle.setPorcentajeDescuento(descuento);
            subtotalNeto = subtotalNeto.add(subtotal);
        }
        // Verificacion de disponibilidad de los productos
        // Si alguno no alcanza, se lanza excepción antes de tocar el stock.
        for (DetalleVenta detalle : venta.getDetalles()) {
            String url = URL_MS_INVENTARIO_DISPONIBILIDAD
                    + "?idProducto=" + detalle.getIdProducto()
                    + "&idSucursal=" + venta.getIdSucursal();
            Integer disponible = restTemplate.getForObject(url, Integer.class);
            if (disponible == null) {
                throw new RecursoNoEncontradoException(
                        "No existe inventario para el producto " + detalle.getIdProducto()
                                + " en la sucursal " + venta.getIdSucursal());
            }
            if (disponible < detalle.getCantidad()) {
                throw new StockInsuficienteException(
                        "Stock insuficiente para el producto " + detalle.getIdProducto()
                                + ": disponible " + disponible + ", solicitado " + detalle.getCantidad());
            }
        }
        // descuentos e iva
        BigDecimal montoDescuento = subtotalNeto
                .multiply(descuento)
                .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
        BigDecimal netoConDescuento = subtotalNeto.subtract(montoDescuento);
        BigDecimal iva = netoConDescuento.multiply(TASA_IVA).setScale(0, RoundingMode.HALF_UP);
        BigDecimal total = netoConDescuento.add(iva);
        venta.setSubtotalNeto(subtotalNeto);
        venta.setIva(iva);
        venta.setTotal(total);
        venta.setFecha(LocalDateTime.now());
        // se guarda la venta
        Venta guardada = ventaRepository.save(venta);
        // Se descuenta stock
        for (DetalleVenta detalle : guardada.getDetalles()) {
            AjusteStockDTO ajuste = new AjusteStockDTO(
                    detalle.getIdProducto(),
                    guardada.getIdSucursal(),
                    -detalle.getCantidad(),
                    "venta-" + guardada.getIdVenta() + "-producto-" + detalle.getIdProducto());
            restTemplate.put(URL_MS_INVENTARIO_AJUSTE, ajuste);
        }
        return guardada;
    }

    public void anularVenta(Long id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la venta con id " + id));
        // Se revierte el stock
        for (DetalleVenta detalle : venta.getDetalles()) {
            AjusteStockDTO ajuste = new AjusteStockDTO(
                    detalle.getIdProducto(),
                    venta.getIdSucursal(),
                    detalle.getCantidad(), // POSITIVO: reingresa lo vendido
                    "anulacion-venta-" + venta.getIdVenta() + "-producto-" + detalle.getIdProducto());
            restTemplate.put(URL_MS_INVENTARIO_AJUSTE, ajuste);
        }
        ventaRepository.delete(venta);
    }

    public Venta actualizarDescuento(Long id, BigDecimal nuevoDescuento) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la venta con id " + id));
        if (nuevoDescuento == null) {
            nuevoDescuento = BigDecimal.ZERO;
        }
        if (nuevoDescuento.compareTo(DESCUENTO_MAXIMO) > 0) {
            throw new DescuentoNoAutorizadoException(
                    "El descuento de " + nuevoDescuento + "% supera el máximo permitido ("
                            + DESCUENTO_MAXIMO + "%) sin autorización de gerente");
        }
        // Recalcular sobre el subtotal neto
        BigDecimal montoDescuento = venta.getSubtotalNeto()
                .multiply(nuevoDescuento)
                .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
        BigDecimal netoConDescuento = venta.getSubtotalNeto().subtract(montoDescuento);
        BigDecimal iva = netoConDescuento.multiply(TASA_IVA).setScale(0, RoundingMode.HALF_UP);
        venta.setPorcentajeDescuento(nuevoDescuento);
        venta.setIva(iva);
        venta.setTotal(netoConDescuento.add(iva));
        for (DetalleVenta detalle : venta.getDetalles()) {
            detalle.setPorcentajeDescuento(nuevoDescuento);
        }
        return ventaRepository.save(venta);
    }

    public Optional<Venta> findById(Long id) {
        return ventaRepository.findById(id);
    }

    public List<Venta> listarVentas() {
        return ventaRepository.findAll();
    }

    public Optional<Venta> getVentaPorPedido(Long idPedido) {
        return ventaRepository.findByIdPedido(idPedido);
    }

    public List<Venta> listarPorSucursal(Long idSucursal) {
        return ventaRepository.findByIdSucursal(idSucursal);
    }
}
