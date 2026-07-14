package com.ventas.ms_ventas.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> manejoErroresValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errores.put(error.getField(), error.getDefaultMessage());
        });
        return errores;
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> manejoRecursoNoEncontrado(RecursoNoEncontradoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return error;
    }

    @ExceptionHandler(DescuentoNoAutorizadoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> manejoDescuentoNoAutorizado(DescuentoNoAutorizadoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return error;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> manejoIntegridadDatos(DataIntegrityViolationException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "El recurso ya existe o viola una restricción de la base de datos");
        return error;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> manejoJsonMalFormado(HttpMessageNotReadableException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "El cuerpo de la solicitud no es un JSON válido o no coincide con la estructura esperada");
        return error;
    }

    @ExceptionHandler(RestClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> manejoErrorComunicacionMS(RestClientException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "No se pudo comunicar con MS Productos y Stock: " + ex.getMessage());
        return error;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> manejoErrorGeneral(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Ocurrió un error inesperado: " + ex.getMessage());
        return error;
    }

    @ExceptionHandler(StockInsuficienteException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> manejoStockInsuficiente(StockInsuficienteException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return error;
    }

    @ExceptionHandler(DevolucionInvalidaException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> manejoDevolucionInvalida(DevolucionInvalidaException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return error;
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> manejoEstadoInvalido(EstadoInvalidoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return error;
    }
}
