package com.ventas.ms_ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.ventas.ms_ventas.exception.DevolucionInvalidaException;
import com.ventas.ms_ventas.exception.RecursoNoEncontradoException;
import com.ventas.ms_ventas.model.DetalleDevolucion;
import com.ventas.ms_ventas.model.DetalleVenta;
import com.ventas.ms_ventas.model.Devolucion;
import com.ventas.ms_ventas.model.Venta;
import com.ventas.ms_ventas.repository.DevolucionRepository;
import com.ventas.ms_ventas.repository.VentaRepository;

@ExtendWith(MockitoExtension.class)
class DevolucionServiceTest {
    @Mock
    private DevolucionRepository devolucionRepository;

    @Mock
    private VentaRepository ventaRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private DevolucionService devolucionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(devolucionService, "URL_MS_INVENTARIO_AJUSTE",
                "http://localhost:9999/api/inventario/ajustar");
    }

    // Venta original: 5 unidades del producto 1 en sucursal 1
    private Venta crearVentaConDetalle(Long idVenta, Long idProducto, int cantidadVendida) {
        DetalleVenta dv = new DetalleVenta();
        dv.setIdProducto(idProducto);
        dv.setCantidad(cantidadVendida);

        Venta venta = new Venta();
        venta.setIdVenta(idVenta);
        venta.setIdSucursal(1L);
        venta.setDetalles(List.of(dv));
        return venta;
    }

    // Devolución que referencia una venta y pide devolver cierta cantidad
    private Devolucion crearDevolucion(Long idVenta, Long idProducto, int cantidadDevolver) {
        DetalleDevolucion dd = new DetalleDevolucion();
        dd.setIdProducto(idProducto);
        dd.setCantidad(cantidadDevolver);

        Venta refVenta = new Venta();
        refVenta.setIdVenta(idVenta);

        Devolucion devolucion = new Devolucion();
        devolucion.setVenta(refVenta);
        devolucion.setDetalles(List.of(dd));
        return devolucion;
    }

    @Test
    void testProcesarDevolucion_valida_reingresaStock() {
        // Vendí 5, devuelvo 2 -> válido
        Venta venta = crearVentaConDetalle(1L, 100L, 5);
        Devolucion devolucion = crearDevolucion(1L, 100L, 2);
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(venta));
        when(devolucionRepository.findByVenta_IdVenta(1L)).thenReturn(List.of()); // sin devoluciones previas
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(inv -> {
            Devolucion d = inv.getArgument(0);
            d.setIdDevolucion(1L);
            return d;
        });
        Devolucion resultado = devolucionService.procesarDevolucion(devolucion);
        assertNotNull(resultado);
        assertNotNull(resultado.getFecha());
        // Reingreso stock: 1 detalle => 1 PUT a inventario/ajustar
        verify(restTemplate, times(1)).put(contains("ajustar"), any());
        verify(devolucionRepository, times(1)).save(any(Devolucion.class));
    }

    @Test
    void testProcesarDevolucion_ventaInexistente_lanzaExcepcion() {
        Devolucion devolucion = crearDevolucion(99L, 100L, 1);
        when(ventaRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> devolucionService.procesarDevolucion(devolucion));
        verify(devolucionRepository, never()).save(any(Devolucion.class));
    }

    @Test
    void testProcesarDevolucion_productoNoPerteneceALaVenta_lanzaExcepcion() {
        // La venta tiene el producto 100, pero intento devolver el 999
        Venta venta = crearVentaConDetalle(1L, 100L, 5);
        Devolucion devolucion = crearDevolucion(1L, 999L, 1);
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(venta));
        when(devolucionRepository.findByVenta_IdVenta(1L)).thenReturn(List.of());
        DevolucionInvalidaException ex = assertThrows(
                DevolucionInvalidaException.class,
                () -> devolucionService.procesarDevolucion(devolucion));
        assertTrue(ex.getMessage().contains("no pertenece"));
        verify(devolucionRepository, never()).save(any(Devolucion.class));
        verify(restTemplate, never()).put(anyString(), any());
    }

    @Test
    void testProcesarDevolucion_devuelveMasDeLoVendido_lanzaExcepcion() {
        // Vendi 5, intento devolver 8 -> invalido
        Venta venta = crearVentaConDetalle(1L, 100L, 5);
        Devolucion devolucion = crearDevolucion(1L, 100L, 8);
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(venta));
        when(devolucionRepository.findByVenta_IdVenta(1L)).thenReturn(List.of());
        DevolucionInvalidaException ex = assertThrows(
                DevolucionInvalidaException.class,
                () -> devolucionService.procesarDevolucion(devolucion));
        assertTrue(ex.getMessage().contains("No se puede devolver"));
        verify(devolucionRepository, never()).save(any(Devolucion.class));
    }

    @Test
    void testProcesarDevolucion_superaConDevolucionesPrevias_lanzaExcepcion() {
        // Vendí 5, ya devolví 4 antes, intento devolver 2 mas -> 4+2=6 > 5 -> inválido
        Venta venta = crearVentaConDetalle(1L, 100L, 5);
        // Devolución previa de 4
        DetalleDevolucion ddPrevia = new DetalleDevolucion();
        ddPrevia.setIdProducto(100L);
        ddPrevia.setCantidad(4);
        Devolucion devPrevia = new Devolucion();
        devPrevia.setDetalles(List.of(ddPrevia));
        Devolucion nueva = crearDevolucion(1L, 100L, 2);
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(venta));
        when(devolucionRepository.findByVenta_IdVenta(1L)).thenReturn(List.of(devPrevia));
        DevolucionInvalidaException ex = assertThrows(
                DevolucionInvalidaException.class,
                () -> devolucionService.procesarDevolucion(nueva));
        assertTrue(ex.getMessage().contains("ya se devolvieron"));
        verify(devolucionRepository, never()).save(any(Devolucion.class));
    }

    @Test
    void testProcesarDevolucion_parcialValidaConPrevias() {
        // Vendí 5, ya devolví 3, devuelvo 2 mas -> 3+2=5 = 5 -> válido (límite exacto)
        Venta venta = crearVentaConDetalle(1L, 100L, 5);
        DetalleDevolucion ddPrevia = new DetalleDevolucion();
        ddPrevia.setIdProducto(100L);
        ddPrevia.setCantidad(3);
        Devolucion devPrevia = new Devolucion();
        devPrevia.setDetalles(List.of(ddPrevia));
        Devolucion nueva = crearDevolucion(1L, 100L, 2);
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(venta));
        when(devolucionRepository.findByVenta_IdVenta(1L)).thenReturn(List.of(devPrevia));
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(inv -> {
            Devolucion d = inv.getArgument(0);
            d.setIdDevolucion(2L);
            return d;
        });
        Devolucion resultado = devolucionService.procesarDevolucion(nueva);
        assertNotNull(resultado);
        verify(devolucionRepository, times(1)).save(any(Devolucion.class));
    
    }

    @Test
    void testAnularDevolucion_revierteStock() {
        DetalleDevolucion dd = new DetalleDevolucion();
        dd.setIdProducto(100L);
        dd.setCantidad(2);
        Venta venta = new Venta();
        venta.setIdSucursal(1L);
        Devolucion devolucion = new Devolucion();
        devolucion.setIdDevolucion(1L);
        devolucion.setVenta(venta);
        devolucion.setDetalles(List.of(dd));
        when(devolucionRepository.findById(1L)).thenReturn(Optional.of(devolucion));
        devolucionService.anularDevolucion(1L);
        // Revierte el reingreso: 1 PUT negativo + elimina la devolución
        verify(restTemplate, times(1)).put(contains("ajustar"), any());
        verify(devolucionRepository, times(1)).delete(devolucion);
    }

    @Test
    void testAnularDevolucion_inexistente_lanzaExcepcion() {
        when(devolucionRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> devolucionService.anularDevolucion(99L));
        verify(devolucionRepository, never()).delete(any(Devolucion.class));
    }

    @Test
    void testFindById_existente() {
        Devolucion devolucion = new Devolucion();
        devolucion.setIdDevolucion(1L);
        when(devolucionRepository.findById(1L)).thenReturn(Optional.of(devolucion));
        Optional<Devolucion> resultado = devolucionService.findById(1L);
        assertTrue(resultado.isPresent());
    }

    @Test
    void testListarDevoluciones() {
        when(devolucionRepository.findAll()).thenReturn(List.of(new Devolucion(), new Devolucion()));
        List<Devolucion> resultado = devolucionService.listarDevoluciones();
        assertEquals(2, resultado.size());
    }

    @Test
    void testListarPorVenta() {
        when(devolucionRepository.findByVenta_IdVenta(1L)).thenReturn(List.of(new Devolucion()));
        List<Devolucion> resultado = devolucionService.listarPorVenta(1L);
        assertEquals(1, resultado.size());
        verify(devolucionRepository, times(1)).findByVenta_IdVenta(1L);
    }
}