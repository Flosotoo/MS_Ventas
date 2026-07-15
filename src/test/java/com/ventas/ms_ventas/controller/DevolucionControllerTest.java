package com.ventas.ms_ventas.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ventas.ms_ventas.exception.DevolucionInvalidaException;
import com.ventas.ms_ventas.exception.RecursoNoEncontradoException;
import com.ventas.ms_ventas.model.DetalleDevolucion;
import com.ventas.ms_ventas.model.Devolucion;
import com.ventas.ms_ventas.model.Venta;
import com.ventas.ms_ventas.service.DevolucionService;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(DevolucionController.class)
class DevolucionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DevolucionService devolucionService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private Devolucion crearDevolucion() {
        DetalleDevolucion dd = new DetalleDevolucion();
        dd.setIdProducto(1L);
        dd.setCantidad(1);
        Venta venta = new Venta();
        venta.setIdVenta(1L);
        Devolucion d = new Devolucion();
        d.setIdDevolucion(1L);
        d.setVenta(venta);
        d.setDetalles(List.of(dd));
        return d;
    }

    @Test
    void testProcesarDevolucion_devuelve201() throws Exception {
        Devolucion d = crearDevolucion();
        when(devolucionService.procesarDevolucion(any(Devolucion.class))).thenReturn(d);
        mockMvc.perform(post("/api/devoluciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(d)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDevolucion").value(1));
    }

    @Test
    void testProcesarDevolucion_invalida_devuelve409() throws Exception {
        Devolucion d = crearDevolucion();
        when(devolucionService.procesarDevolucion(any(Devolucion.class)))
                .thenThrow(new DevolucionInvalidaException("No se puede devolver más de lo vendido"));
        mockMvc.perform(post("/api/devoluciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(d)))
                .andExpect(status().isConflict());
    }

    @Test
    void testGetDevoluciones_devuelve200() throws Exception {
        when(devolucionService.listarDevoluciones()).thenReturn(List.of(crearDevolucion()));
        mockMvc.perform(get("/api/devoluciones"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetDevoluciones_vacio_devuelve204() throws Exception {
        when(devolucionService.listarDevoluciones()).thenReturn(List.of());
        mockMvc.perform(get("/api/devoluciones"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetDevoluciones_porVenta_devuelve200() throws Exception {
        when(devolucionService.listarPorVenta(1L)).thenReturn(List.of(crearDevolucion()));
        mockMvc.perform(get("/api/devoluciones").param("idVenta", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetDevolucion_existente_devuelve200() throws Exception {
        when(devolucionService.findById(1L)).thenReturn(Optional.of(crearDevolucion()));
        mockMvc.perform(get("/api/devoluciones/1"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetDevolucion_inexistente_devuelve404() throws Exception {
        when(devolucionService.findById(9999L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/devoluciones/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testAnularDevolucion_devuelve204() throws Exception {
        doNothing().when(devolucionService).anularDevolucion(1L);
        mockMvc.perform(delete("/api/devoluciones/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testAnularDevolucion_inexistente_devuelve404() throws Exception {
        doThrow(new RecursoNoEncontradoException("No existe"))
                .when(devolucionService).anularDevolucion(anyLong());
        mockMvc.perform(delete("/api/devoluciones/9999"))
                .andExpect(status().isNotFound());
    }

}
