package com.ventas.ms_ventas.exception;

public class DescuentoNoAutorizadoException extends RuntimeException {
    public DescuentoNoAutorizadoException(String mensaje) {
        super(mensaje);
    }

}
