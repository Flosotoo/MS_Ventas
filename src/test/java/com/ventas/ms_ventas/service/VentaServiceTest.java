package com.ventas.ms_ventas.service;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

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

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock
    private VentaRepository ventaRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private VentaService ventaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ventaService, "URL_MS_PRODUCTOS", "http://localhost:9999/api/productos/");
        ReflectionTestUtils.setField(ventaService, "URL_MS_INVENTARIO_AJUSTE",
                "http://localhost:9999/api/inventario/ajustar");
        ReflectionTestUtils.setField(ventaService, "URL_MS_INVENTARIO_DISPONIBILIDAD",
                "http://localhost:9999/api/inventario/disponibilidad");
        ReflectionTestUtils.setField(ventaService, "URL_MS_CLIENTES", "http://localhost:9999/api/clientes/");
        ReflectionTestUtils.setField(ventaService, "URL_MS_PEDIDOS", "http://localhost:9999/api/pedidos/");
        ReflectionTestUtils.setField(ventaService, "URL_MS_PEDIDOS_ESTADO", "http://localhost:9999/api/pedidos/");
        ReflectionTestUtils.setField(ventaService, "URL_MS_SUCURSALES", "http://localhost:9999/api/v1/sucursales/");
        ReflectionTestUtils.setField(ventaService, "URL_MS_INVENTARIO_CONFIRMAR", "http://localhost:9999/api/inventario/confirmar-reserva");
    }

    // Venta de 2 unidades con descuento
    private Venta crearVenta(BigDecimal descuento) {
        DetalleVenta detalle = new DetalleVenta();
        detalle.setIdProducto(1L);
        detalle.setCantidad(2);
        detalle.setPrecioUnitario(new BigDecimal("45000"));

        Venta venta = new Venta();
        venta.setIdSucursal(1L);
        venta.setPorcentajeDescuento(descuento);
        venta.setDetalles(List.of(detalle));
        return venta;
    }

    private void mockSucursalValida() {
        SucursalDTO sucursal = new SucursalDTO();
        sucursal.setIdSucursal(1L);
        sucursal.setNombre("Sucursal Centro");
        when(restTemplate.getForObject(anyString(), eq(SucursalDTO.class))).thenReturn(sucursal);
    }

    private PedidoDTO crearPedidoValido() {
        PedidoDTO.DetallePedidoDTO dp = new PedidoDTO.DetallePedidoDTO();
        dp.setIdProducto(1L);
        dp.setCantidad(2);
        dp.setPrecioUnitario(new BigDecimal("45000"));

        PedidoDTO pedido = new PedidoDTO();
        pedido.setIdPedido(5L);
        pedido.setIdCliente(3L);
        pedido.setEstado("PAGADO");
        pedido.setTipoEntrega("RETIRO_TIENDA");
        pedido.setIdSucursalRetiro(1L);
        pedido.setTotal(new BigDecimal("90000"));
        pedido.setDetalles(List.of(dp));
        return pedido;
    }

    @Test
    void testRegistrarVentaDirecta_calculaIVAyTotalCorrectamente() {
        // 2 x 45000 = 90000 neto * IVA 19% = 17100; total = 107100
        Venta venta = crearVenta(BigDecimal.ZERO);
        mockSucursalValida();
        ProductoDTO producto = new ProductoDTO();
        producto.setIdProducto(1L);
        producto.setNombre("Perfume Test");
        when(restTemplate.getForObject(anyString(), eq(ProductoDTO.class))).thenReturn(producto);
        when(restTemplate.getForObject(anyString(), eq(Integer.class))).thenReturn(50);
        // save devuelve la misma venta (con id asignado)
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocacion -> {
            Venta v = invocacion.getArgument(0);
            v.setIdVenta(1L);
            return v;
        });
        Venta resultado = ventaService.registrarVentaDirecta(venta);
        assertNotNull(resultado);
        assertEquals(new BigDecimal("90000"), resultado.getSubtotalNeto());
        assertEquals(new BigDecimal("17100"), resultado.getIva());
        assertEquals(new BigDecimal("107100"), resultado.getTotal());
        verify(ventaRepository, times(1)).save(any(Venta.class));
        // se descontó stock: 1 producto => 1 PUT a inventario/ajustar
        verify(restTemplate, times(1)).put(contains("ajustar"), any());
    }

    @Test
    void testRegistrarVentaDirecta_descuentoSobreTope_lanzaExcepcion() {
        // Se supera el máximo de 50% => DescuentoNoAutorizadoException
        //El descuento se valida antes de validar la sucursal, por ende no hay necesidad de mockearla
        Venta venta = crearVenta(new BigDecimal("60"));
        DescuentoNoAutorizadoException ex = assertThrows(
                DescuentoNoAutorizadoException.class,
                () -> ventaService.registrarVentaDirecta(venta));
        assertTrue(ex.getMessage().contains("supera el máximo permitido"));
        // No debe guardar ni tocar stock
        verify(ventaRepository, never()).save(any(Venta.class));
        verify(restTemplate, never()).put(anyString(), any());
    }

    @Test
    void testRegistrarVentaDirecta_stockInsuficiente_lanzaExcepcion() {
        // Pide 2 pero solo hay 1 disponible -> StockInsuficienteException
        Venta venta = crearVenta(BigDecimal.ZERO);
        mockSucursalValida();
        ProductoDTO producto = new ProductoDTO();
        producto.setIdProducto(1L);
        producto.setNombre("Perfume Test");
        when(restTemplate.getForObject(anyString(), eq(ProductoDTO.class))).thenReturn(producto);
        when(restTemplate.getForObject(anyString(), eq(Integer.class))).thenReturn(1); // solo 1
        StockInsuficienteException ex = assertThrows(
                StockInsuficienteException.class,
                () -> ventaService.registrarVentaDirecta(venta));
        assertTrue(ex.getMessage().contains("Stock insuficiente"));
        // La verificación evita guardar y descontar stock
        verify(ventaRepository, never()).save(any(Venta.class));
        verify(restTemplate, never()).put(anyString(), any());
    }

    @Test
    void testAnularVenta_revierteStock() {
        DetalleVenta detalle = new DetalleVenta();
        detalle.setIdProducto(1L);
        detalle.setCantidad(2);
        Venta venta = new Venta();
        venta.setIdVenta(1L);
        venta.setIdSucursal(1L);
        venta.setDetalles(List.of(detalle));
        when(ventaRepository.findById(1L)).thenReturn(java.util.Optional.of(venta));
        ventaService.anularVenta(1L);
        // Reingresa el stock (1 PUT) y elimina la venta
        verify(restTemplate, times(1)).put(contains("ajustar"), any());
        verify(ventaRepository, times(1)).delete(venta);
    }

    @Test
    void testActualizarDescuento_recalculaIVAyTotal() {
        // Venta con subtotal 90000; aplico 20% descuento
        Venta venta = new Venta();
        venta.setIdVenta(1L);
        venta.setSubtotalNeto(new BigDecimal("90000"));
        venta.setDetalles(List.of(new DetalleVenta()));
        when(ventaRepository.findById(1L)).thenReturn(java.util.Optional.of(venta));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));
        Venta resultado = ventaService.actualizarDescuento(1L, new BigDecimal("20"));
        // 90000 - 20% = 72000; IVA 19% = 13680; total = 85680
        assertEquals(new BigDecimal("72000"), resultado.getSubtotalNeto().subtract(
                resultado.getSubtotalNeto().multiply(new BigDecimal("20")).divide(new BigDecimal("100"))));
        assertEquals(new BigDecimal("13680"), resultado.getIva());
        assertEquals(new BigDecimal("85680"), resultado.getTotal());
    }

    @Test
    void testActualizarDescuento_sobreTope_lanzaExcepcion() {
        Venta venta = new Venta();
        venta.setIdVenta(1L);
        venta.setSubtotalNeto(new BigDecimal("90000"));
        when(ventaRepository.findById(1L)).thenReturn(java.util.Optional.of(venta));
        assertThrows(DescuentoNoAutorizadoException.class,
                () -> ventaService.actualizarDescuento(1L, new BigDecimal("60")));
        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    void testGetVentaPorPedido() {
        Venta venta = new Venta();
        venta.setIdVenta(1L);
        when(ventaRepository.findByIdPedido(5L)).thenReturn(java.util.Optional.of(venta));
        java.util.Optional<Venta> resultado = ventaService.getVentaPorPedido(5L);
        assertTrue(resultado.isPresent());
    }

    @Test
    void testListarPorSucursal() {
        when(ventaRepository.findByIdSucursal(1L)).thenReturn(List.of(new Venta(), new Venta()));
        List<Venta> resultado = ventaService.listarPorSucursal(1L);
        assertEquals(2, resultado.size());
    }

    @Test
    void testListarVentas() {
        when(ventaRepository.findAll()).thenReturn(List.of(new Venta()));
        List<Venta> resultado = ventaService.listarVentas();
        assertEquals(1, resultado.size());
    }

    @Test
    void testRegistrarRetiro_exitoso_confirmaReservaYCierraPedido() {
        PedidoDTO pedido = crearPedidoValido();
        when(ventaRepository.findByIdPedido(5L)).thenReturn(java.util.Optional.empty());
        when(restTemplate.getForObject(anyString(), eq(PedidoDTO.class))).thenReturn(pedido);

        ProductoDTO producto = new ProductoDTO();
        producto.setIdProducto(1L);
        when(restTemplate.getForObject(anyString(), eq(ProductoDTO.class))).thenReturn(producto);

        // NUEVO: sucursal válida (es lo que faltaba y causó el error)
        SucursalDTO sucursal = new SucursalDTO();
        sucursal.setIdSucursal(1L);
        when(restTemplate.getForObject(anyString(), eq(SucursalDTO.class))).thenReturn(sucursal);

        // NUEVO: cliente válido (idCliente=3 no es null, así que también se valida)
        ClienteDTO cliente = new ClienteDTO();
        cliente.setIdCliente(3L);
        when(restTemplate.getForObject(anyString(), eq(ClienteDTO.class))).thenReturn(cliente);

        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> {
            Venta v = inv.getArgument(0);
            v.setIdVenta(1L);
            return v;
        });

        Venta resultado = ventaService.registrarRetiro(5L);

        assertNotNull(resultado);
        assertEquals(5L, resultado.getIdPedido());
        verify(restTemplate, times(1)).put(contains("confirmar-reserva"), any());
        verify(restTemplate, never()).put(contains("ajustar"), any());
    }

    @Test
    void testRegistrarRetiro_pedidoYaRetirado_lanzaExcepcion() {
        Venta existente = new Venta();
        existente.setIdVenta(1L);
        when(ventaRepository.findByIdPedido(5L)).thenReturn(java.util.Optional.of(existente));

        assertThrows(EstadoInvalidoException.class,
                () -> ventaService.registrarRetiro(5L));

        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    void testRegistrarRetiro_pedidoInexistente_lanzaExcepcion() {
        when(ventaRepository.findByIdPedido(5L)).thenReturn(java.util.Optional.empty());
        when(restTemplate.getForObject(anyString(), eq(PedidoDTO.class))).thenReturn(null);

        assertThrows(RecursoNoEncontradoException.class,
                () -> ventaService.registrarRetiro(5L));
    }

    @Test
    void testRegistrarRetiro_noEsRetiroTienda_lanzaExcepcion() {
        PedidoDTO pedido = crearPedidoValido();
        pedido.setTipoEntrega("DESPACHO_DOMICILIO"); // no es retiro
        when(ventaRepository.findByIdPedido(5L)).thenReturn(java.util.Optional.empty());
        when(restTemplate.getForObject(anyString(), eq(PedidoDTO.class))).thenReturn(pedido);

        assertThrows(EstadoInvalidoException.class,
                () -> ventaService.registrarRetiro(5L));

        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    void testRegistrarRetiro_noPagado_lanzaExcepcion() {
        PedidoDTO pedido = crearPedidoValido();
        pedido.setEstado("PENDIENTE_PAGO"); // no está pagado
        when(ventaRepository.findByIdPedido(5L)).thenReturn(java.util.Optional.empty());
        when(restTemplate.getForObject(anyString(), eq(PedidoDTO.class))).thenReturn(pedido);

        assertThrows(EstadoInvalidoException.class,
                () -> ventaService.registrarRetiro(5L));
    }

    @Test
    void testRegistrarRetiro_sinDetalles_lanzaExcepcion() {
        PedidoDTO pedido = crearPedidoValido();
        pedido.setDetalles(java.util.List.of()); // sin detalles
        when(ventaRepository.findByIdPedido(5L)).thenReturn(java.util.Optional.empty());
        when(restTemplate.getForObject(anyString(), eq(PedidoDTO.class))).thenReturn(pedido);

        assertThrows(RecursoNoEncontradoException.class,
                () -> ventaService.registrarRetiro(5L));
    }

    @Test
    void testAnularVenta_presencial_reingresaStock() {
        DetalleVenta detalle = new DetalleVenta();
        detalle.setIdProducto(1L);
        detalle.setCantidad(2);
        Venta venta = new Venta();
        venta.setIdVenta(1L);
        venta.setIdSucursal(1L);
        venta.setIdPedido(null); // presencial
        venta.setDetalles(List.of(detalle));
        when(ventaRepository.findById(1L)).thenReturn(java.util.Optional.of(venta));

        ventaService.anularVenta(1L);

        verify(restTemplate, times(1)).put(contains("ajustar"), any());
        verify(ventaRepository, times(1)).delete(venta);
    }

    @Test
    void testAnularVenta_retiroWeb_lanzaExcepcion() {
        Venta venta = new Venta();
        venta.setIdVenta(1L);
        venta.setIdPedido(5L); // proviene de retiro web
        when(ventaRepository.findById(1L)).thenReturn(java.util.Optional.of(venta));

        assertThrows(EstadoInvalidoException.class,
                () -> ventaService.anularVenta(1L));

        verify(ventaRepository, never()).delete(any(Venta.class));
    }

    @Test
    void testAnularVenta_inexistente_lanzaExcepcion() {
        when(ventaRepository.findById(99L)).thenReturn(java.util.Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> ventaService.anularVenta(99L));
    }
}
