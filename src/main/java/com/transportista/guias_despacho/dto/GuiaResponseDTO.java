package com.transportista.guias_despacho.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class GuiaResponseDTO {
    private String id;
    private String numeroGuia;
    private String transportista;
    private String destinatario;
    private String direccionDestino;
    private String descripcionCarga;
    private Double pesoKg;
    private String estado;
    private LocalDate fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private String rutaS3;
    private String urlDescarga;
    private String mensaje;
}