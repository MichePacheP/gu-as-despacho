package com.transportista.guias_despacho.exception;

public class GuiaNotFoundException extends RuntimeException {
    public GuiaNotFoundException(String mensaje) {
        super(mensaje);
    }
}