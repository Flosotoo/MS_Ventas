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
import com.ventas.ms_ventas.model.DetalleVenta;
import com.ventas.ms_ventas.model.Venta;
import com.ventas.ms_ventas.repository.VentaRepository;

@Service
public class VentaService {
    private static final Logger log = LoggerFactory.getLogger(VentaService.class);
    
    //Tope de descuento sin autorizacion
    private static final BigDecimal DESCUENTO_MAXIMO = new BigDecimal("50");
    
    //IVA
    private static final BigDecimal TASA_IVA = new BigDecimal("0.19");

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${ms.productos.url}")
    private String URL_MS_PRODUCTOS;

    @Value("${ms.inventario.ajuste.url}")
    private String URL_MS_INVENTARIO_AJUSTE;

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
        //Validacion de cada producto contra MS Productos y calcular subtotales
        BigDecimal subtotalNeto = BigDecimal.ZERO;
        for (DetalleVenta detalle : venta.getDetalles()) {
            String url = URL_MS_PRODUCTOS + detalle.getIdProducto();
            ProductoDTO producto = restTemplate.getForObject(url, ProductoDTO.class);
            if (producto == null) {
                throw new RecursoNoEncontradoException(
                        "El producto " + detalle.getIdProducto() + " no existe en el catálogo");
            }
            detalle.setVenta(venta); // completa el lado dueño de la relación
            BigDecimal subtotal = detalle.getPrecioUnitario()
                    .multiply(BigDecimal.valueOf(detalle.getCantidad()));
            detalle.setSubtotal(subtotal);
            subtotalNeto = subtotalNeto.add(subtotal);
        }
        // Aplicar descuento sobre el neto
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

        // Guardar la venta
        Venta guardada = ventaRepository.save(venta);

        // desconta stock físico vía MS Productos y Stock
        //  idOperacion único por línea para idempotencia (no duplicar si hay reintento).
        for (DetalleVenta detalle : guardada.getDetalles()) {
            AjusteStockDTO ajuste = new AjusteStockDTO(
                    detalle.getIdProducto(),
                    guardada.getIdSucursal(),
                    -detalle.getCantidad(), // NEGATIVO: una venta resta stock
                    "venta-" + guardada.getIdVenta() + "-producto-" + detalle.getIdProducto());
            restTemplate.put(URL_MS_INVENTARIO_AJUSTE, ajuste);
        }
        return guardada;
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
