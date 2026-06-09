package com.transportista.guias_despacho.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuiaDespacho {

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
    private String rutaEfs;
    private String rutaS3;
    private String urlDescarga;
}