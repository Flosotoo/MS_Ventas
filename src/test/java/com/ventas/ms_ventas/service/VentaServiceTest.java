package com.ventas.ms_ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.ventas.ms_ventas.dto.ProductoDTO;
import com.ventas.ms_ventas.dto.SucursalDTO;
import com.ventas.ms_ventas.exception.DescuentoNoAutorizadoException;
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
}
