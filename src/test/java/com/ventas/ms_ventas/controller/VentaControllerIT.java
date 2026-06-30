package com.ventas.ms_ventas.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import com.ventas.ms_ventas.dto.ProductoDTO;
import com.ventas.ms_ventas.dto.SucursalDTO;
import com.ventas.ms_ventas.model.DetalleVenta;
import com.ventas.ms_ventas.model.Venta;
import com.ventas.ms_ventas.repository.VentaRepository;

import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")

class VentaControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VentaRepository ventaRepository;

    @MockitoBean
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ventaRepository.deleteAll();
        // Producto existe en el catálogo
        ProductoDTO producto = new ProductoDTO();
        producto.setIdProducto(1L);
        producto.setNombre("Perfume Test");
        when(restTemplate.getForObject(anyString(), eq(ProductoDTO.class))).thenReturn(producto);
        // Hay 50 unidades disponibles
        when(restTemplate.getForObject(anyString(), eq(Integer.class))).thenReturn(50);
        // Validar sucursal
        SucursalDTO sucursal = new SucursalDTO();
        sucursal.setIdSucursal(1L);
        sucursal.setNombre("Sucursal Centro");
        when(restTemplate.getForObject(anyString(), eq(SucursalDTO.class))).thenReturn(sucursal);
        // El PUT de descuento de stock no devuelve nada relevante (void)
        doNothing().when(restTemplate).put(anyString(), any());
    }

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

    @Test
    void testRegistrarVenta_devuelve201YCalculaTotales() throws Exception {
        Venta venta = crearVenta(BigDecimal.ZERO);
        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(venta)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idVenta").exists())
                .andExpect(jsonPath("$.subtotalNeto").value(90000))
                .andExpect(jsonPath("$.iva").value(17100))
                .andExpect(jsonPath("$.total").value(107100));
    }

    @Test
    void testRegistrarVenta_descuentoSobreTope_devuelve409() throws Exception {
        Venta venta = crearVenta(new BigDecimal("60")); // supera el 50%
        mockMvc.perform(post("/api/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(venta)))
                .andExpect(status().isConflict());
    }

    @Test
    void testGetVenta_inexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/api/ventas/9999"))
                .andExpect(status().isNotFound());
    }
}