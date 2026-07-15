package com.ventas.ms_ventas.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ventas.ms_ventas.exception.DescuentoNoAutorizadoException;
import com.ventas.ms_ventas.model.DetalleVenta;
import com.ventas.ms_ventas.model.Venta;
import com.ventas.ms_ventas.service.VentaService;


@WebMvcTest(VentaController.class)
class VentaControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VentaService ventaService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private Venta crearVentaConTotales() {
        DetalleVenta detalle = new DetalleVenta();
        detalle.setIdProducto(1L);
        detalle.setCantidad(2);
        detalle.setPrecioUnitario(new BigDecimal("45000"));
        Venta venta = new Venta();
        venta.setIdVenta(1L);
        venta.setIdSucursal(1L);
        venta.setPorcentajeDescuento(BigDecimal.ZERO);
        venta.setSubtotalNeto(new BigDecimal("90000"));
        venta.setIva(new BigDecimal("17100"));
        venta.setTotal(new BigDecimal("107100"));
        venta.setDetalles(List.of(detalle));
        return venta;
    }

    @Test
    void testRegistrarVenta_devuelve201() throws Exception {
        Venta venta = crearVentaConTotales();
        // Cuando el controller llame al service, devolvemos la venta ya "procesada"
        when(ventaService.registrarVentaDirecta(any(Venta.class))).thenReturn(venta);
        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(venta)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idVenta").value(1))
                .andExpect(jsonPath("$.total").value(107100));
        verify(ventaService, times(1)).registrarVentaDirecta(any(Venta.class));
    }

    @Test
    void testGetVenta_existente_devuelve200() throws Exception {
        Venta venta = crearVentaConTotales();
        when(ventaService.findById(1L)).thenReturn(Optional.of(venta));
        mockMvc.perform(get("/api/ventas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idVenta").value(1));
        verify(ventaService, times(1)).findById(1L);
    }

    @Test
    void testGetVenta_inexistente_devuelve404() throws Exception {
        // El service devuelve vacío => el controller lanza RecursoNoEncontradoException => 404
        when(ventaService.findById(9999L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/ventas/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testRegistrarVenta_descuentoSobreTope_devuelve409() throws Exception {
        Venta venta = crearVentaConTotales();
        venta.setPorcentajeDescuento(new BigDecimal("60"));
        // Se simula service rechazando descuento
        when(ventaService.registrarVentaDirecta(any(Venta.class)))
                .thenThrow(new DescuentoNoAutorizadoException(
                        "El descuento de 60% supera el máximo permitido (50%)"));
        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(venta)))
                .andExpect(status().isConflict());
    }

    @Test
    void testGetVentas_devuelve200() throws Exception {
        Venta venta = crearVentaConTotales();
        when(ventaService.listarVentas()).thenReturn(java.util.List.of(venta));
        mockMvc.perform(get("/api/ventas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idVenta").value(1));
    }

    @Test
    void testGetVentas_vacio_devuelve204() throws Exception {
        when(ventaService.listarVentas()).thenReturn(java.util.List.of());
        mockMvc.perform(get("/api/ventas"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetVentas_porSucursal_devuelve200() throws Exception {
        when(ventaService.listarPorSucursal(1L)).thenReturn(java.util.List.of(crearVentaConTotales()));
        mockMvc.perform(get("/api/ventas").param("idSucursal", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void testRegistrarVenta_stockInsuficiente_devuelve409() throws Exception {
        Venta venta = crearVentaConTotales();
        when(ventaService.registrarVentaDirecta(any(Venta.class)))
                .thenThrow(new com.ventas.ms_ventas.exception.StockInsuficienteException(
                        "Stock insuficiente para el producto 1"));
        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(venta)))
                .andExpect(status().isConflict());
    }

    @Test
    void testRegistrarRetiro_devuelve201() throws Exception {
        Venta venta = crearVentaConTotales(); // tu helper existente
        when(ventaService.registrarRetiro(5L)).thenReturn(venta);

        mockMvc.perform(post("/api/ventas/retiro/5"))
                .andExpect(status().isCreated());
    }

    @Test
    void testRegistrarRetiro_pedidoYaRetirado_devuelve409() throws Exception {
        when(ventaService.registrarRetiro(5L))
                .thenThrow(new com.ventas.ms_ventas.exception.EstadoInvalidoException("Ya fue retirado"));

        mockMvc.perform(post("/api/ventas/retiro/5"))
                .andExpect(status().isConflict());
    }
}
