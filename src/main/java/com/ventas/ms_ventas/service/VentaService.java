package com.ventas.ms_ventas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.ventas.ms_ventas.dto.AjusteStockDTO;
import com.ventas.ms_ventas.dto.ClienteDTO;
import com.ventas.ms_ventas.dto.PedidoDTO;
import com.ventas.ms_ventas.dto.ProductoDTO;
import com.ventas.ms_ventas.dto.SucursalDTO;
import com.ventas.ms_ventas.exception.DescuentoNoAutorizadoException;
import com.ventas.ms_ventas.exception.EstadoInvalidoException;
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

    @Value("${ms.clientes.url}")
    private String URL_MS_CLIENTES;

    @Value("${ms.pedidos.url}")
    private String URL_MS_PEDIDOS;

    @Value("${ms.pedidos.estado.url}")
    private String URL_MS_PEDIDOS_ESTADO;

    @Value("${ms.inventario.disponibilidad.url}")
    private String URL_MS_INVENTARIO_DISPONIBILIDAD;

    @Value("${ms.sucursales.url}")
    private String URL_MS_SUCURSALES;

    @Value("${ms.inventario.confirmar.url}")
    private String URL_MS_INVENTARIO_CONFIRMAR;

    public Venta registrarVentaDirecta(Venta venta) {
        Venta guardada = calcularYGuardarVenta(venta, true); // sí verifica disponibilidad
        descontarStock(guardada);
        return guardada;
    }

    private Venta calcularYGuardarVenta(Venta venta, boolean verificarDisponibilidad) {
        BigDecimal descuento = (venta.getPorcentajeDescuento() != null)
                ? venta.getPorcentajeDescuento()
                : BigDecimal.ZERO;
        if (descuento.compareTo(DESCUENTO_MAXIMO) > 0) {
            throw new DescuentoNoAutorizadoException(
                    "El descuento de " + descuento + "% supera el máximo permitido ("
                    + DESCUENTO_MAXIMO + "%) sin autorización de gerente");
        }
        venta.setPorcentajeDescuento(descuento);

        validarSucursal(venta.getIdSucursal());
        validarCliente(venta.getIdCliente());
        validarPedido(venta.getIdPedido());

        // Validación de cada producto y cálculo de subtotales
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

        // Verificación de disponibilidad solo en venta presencial.
        // En el retiro web el stock ya está reservado por MS Envíos, así que
        // no figura como disponible y verificarlo daría un falso "stock insuficiente".
        if (verificarDisponibilidad) {
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
        }

        // Descuentos e IVA
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

        return ventaRepository.save(venta);
    }

    // HU-55 -> Registrar retiro del pedido
    public Venta registrarRetiro(Long idPedido) {
        //Un pedido solo se retira una vez
        if (ventaRepository.findByIdPedido(idPedido).isPresent()) {
            throw new EstadoInvalidoException(
                    "El pedido " + idPedido + " ya fue retirado y tiene una venta asociada");
        }

        //Se traer el pedido real desde MS Envíos
        PedidoDTO pedido = restTemplate.getForObject(URL_MS_PEDIDOS + idPedido, PedidoDTO.class);
        if (pedido == null) {
            throw new RecursoNoEncontradoException("El pedido " + idPedido + " no existe");
        }

        //Reglas de negocio del retiro
        if (!"RETIRO_TIENDA".equals(pedido.getTipoEntrega())) {
            throw new EstadoInvalidoException(
                    "El pedido " + idPedido + " es de despacho a domicilio, no se retira en tienda");
        }
        if (!"PAGADO".equals(pedido.getEstado()) && !"LISTO_PARA_RETIRO".equals(pedido.getEstado())) {
            throw new EstadoInvalidoException(
                    "El pedido " + idPedido + " no está en condiciones de retiro (estado: "
                    + pedido.getEstado() + ")");
        }
        if (pedido.getDetalles() == null || pedido.getDetalles().isEmpty()) {
            throw new RecursoNoEncontradoException("El pedido " + idPedido + " no tiene detalles");
        }

        //Se construye la venta a partir del pedido y no del cuerpo del cliente
        Venta venta = new Venta();
        venta.setIdPedido(pedido.getIdPedido());
        venta.setIdCliente(pedido.getIdCliente());
        venta.setIdSucursal(pedido.getIdSucursalRetiro());
        venta.setPorcentajeDescuento(BigDecimal.ZERO);

        List<DetalleVenta> detalles = new ArrayList<>();
        for (PedidoDTO.DetallePedidoDTO dp : pedido.getDetalles()) {
            DetalleVenta dv = new DetalleVenta();
            dv.setIdProducto(dp.getIdProducto());
            dv.setCantidad(dp.getCantidad());
            dv.setPrecioUnitario(dp.getPrecioUnitario());
            detalles.add(dv);
        }
        venta.setDetalles(detalles);

        //Calcular y guardar (sin verificar disponibilidad, ya está reservado)
        Venta guardada = calcularYGuardarVenta(venta, false);

        //CONFIRMAR la reserva que hizo Envíos (no descontar, sería doble)
        confirmarReservaStock(guardada);

        //Cerrar el pedido en Envíos
        marcarPedidoRetirado(idPedido);

        return guardada;
    }

    private void descontarStock(Venta venta) {
        for (DetalleVenta detalle : venta.getDetalles()) {
            AjusteStockDTO ajuste = new AjusteStockDTO(
                    detalle.getIdProducto(),
                    venta.getIdSucursal(),
                    -detalle.getCantidad(),
                    "venta-" + venta.getIdVenta() + "-producto-" + detalle.getIdProducto());
            restTemplate.put(URL_MS_INVENTARIO_AJUSTE, ajuste);
        }
    }

    private void confirmarReservaStock(Venta venta) {
        for (DetalleVenta detalle : venta.getDetalles()) {
            AjusteStockDTO peticion = new AjusteStockDTO(
                    detalle.getIdProducto(),
                    venta.getIdSucursal(),
                    detalle.getCantidad(),
                    null);
            restTemplate.put(URL_MS_INVENTARIO_CONFIRMAR, peticion);
        }
    }

    private void marcarPedidoRetirado(Long idPedido) {
        try {
            String url = URL_MS_PEDIDOS_ESTADO + idPedido + "/estado?nuevoEstado=RETIRADO";
            restTemplate.put(url, null);
        } catch (ResourceAccessException ex) {
            log.warn("No se pudo marcar el pedido {} como RETIRADO en MS Envíos: {}",
                    idPedido, ex.getMessage());
        }
    }

    public void anularVenta(Long id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la venta con id " + id));
        // Una venta de retiro web no se anula aquí: el pedido se cancela en MS Envíos,
        // que sabe cómo revertir su propia reserva de stock.
        if (venta.getIdPedido() != null) {
            throw new EstadoInvalidoException(
                    "La venta " + id + " proviene del pedido web " + venta.getIdPedido()
                    + ". Su cancelación debe gestionarse en MS Envíos.");
        }
        // Venta presencial: reingresa el stock que se descontó
        for (DetalleVenta detalle : venta.getDetalles()) {
            AjusteStockDTO ajuste = new AjusteStockDTO(
                    detalle.getIdProducto(),
                    venta.getIdSucursal(),
                    detalle.getCantidad(),
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

    private void validarCliente(Long idCliente) {
        // Solo se valida si viene (en venta presencial anónima es null y se omite)
        if (idCliente == null) {
            return;
        }
        String url = URL_MS_CLIENTES + idCliente;
        try {
            ClienteDTO cliente = restTemplate.getForObject(url, ClienteDTO.class);
            if (cliente == null) {
                throw new RecursoNoEncontradoException("El cliente " + idCliente + " no existe");
            }
        } catch (ResourceAccessException ex) {
            // MS Clientes no disponible: no bloquear, solo advertir (degradación elegante)
            log.warn("No se pudo validar el cliente {} contra MS Clientes (se omite validación): {}",
                    idCliente, ex.getMessage());
        }
    }

    private void validarPedido(Long idPedido) {
        // Solo se valida si viene (en venta presencial directa es null y se omite)
        if (idPedido == null) {
            return;
        }
        String url = URL_MS_PEDIDOS + idPedido;
        try {
            PedidoDTO pedido = restTemplate.getForObject(url, PedidoDTO.class);
            if (pedido == null) {
                throw new RecursoNoEncontradoException("El pedido " + idPedido + " no existe");
            }
        } catch (ResourceAccessException ex) {
            log.warn("No se pudo validar el pedido {} contra MS Envíos (se omite validación): {}",
                    idPedido, ex.getMessage());
        }
    }

    private void validarSucursal(Long idSucursal) {
        // idSucursal es obligatorio en toda venta, así que siempre se valida
        String url = URL_MS_SUCURSALES + idSucursal;
        try {
            SucursalDTO sucursal = restTemplate.getForObject(url, SucursalDTO.class);
            if (sucursal == null) {
                throw new RecursoNoEncontradoException("La sucursal " + idSucursal + " no existe");
            }
        } catch (ResourceAccessException ex) {
            // Advertencia en caso d no tener disponibilidad
            log.warn("No se pudo validar la sucursal {} contra MS Sucursales (se omite validación): {}",
                    idSucursal, ex.getMessage());
        }
    }
}
