package com.transportista.guias_despacho.exception;

public class PermisoDenegadoException extends RuntimeException {
    public PermisoDenegadoException(String mensaje) {
        super(mensaje);
    }
}