package com.transportista.guias_despacho.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.transportista.guias_despacho.dto.GuiaRequestDTO;
import com.transportista.guias_despacho.dto.GuiaResponseDTO;
import com.transportista.guias_despacho.exception.GuiaNotFoundException;
import com.transportista.guias_despacho.exception.PermisoDenegadoException;
import com.transportista.guias_despacho.model.GuiaDespacho;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuiaService {

    private final S3Service s3Service;

    @Value("${efs.mount-path}")
    private String efsMountPath;

    private final Map<String, GuiaDespacho> guiasStore = new ConcurrentHashMap<>();

    private final Set<String> transportistasPermitidos = new HashSet<>(
            Arrays.asList("transportistaa", "transportistab", "transportistac", "admin")
    );

    public GuiaResponseDTO crearGuia(GuiaRequestDTO request) throws IOException {
        log.info("Creando guía para transportista: {}", request.getTransportista());

        String id = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String numeroGuia = "GUIA-" + id;
        LocalDate hoy = LocalDate.now();

        GuiaDespacho guia = GuiaDespacho.builder()
                .id(id)
                .numeroGuia(numeroGuia)
                .transportista(request.getTransportista())
                .destinatario(request.getDestinatario())
                .direccionDestino(request.getDireccionDestino())
                .descripcionCarga(request.getDescripcionCarga())
                .pesoKg(request.getPesoKg())
                .estado("CREADA")
                .fechaCreacion(hoy)
                .fechaActualizacion(LocalDateTime.now())
                .build();

        Path rutaEfs = generarPdfEnEfs(guia);
        guia.setRutaEfs(rutaEfs.toString());

        String claveS3 = s3Service.generarClaveS3(
                request.getTransportista(),
                numeroGuia + ".pdf",
                hoy
        );
        s3Service.subirArchivo(rutaEfs, claveS3);
        guia.setRutaS3(claveS3);
        guia.setEstado("SUBIDA_S3");

        guiasStore.put(id, guia);

        log.info("Guía creada y subida a S3: {}", numeroGuia);
        return mapToResponse(guia, "Guía creada y subida a S3 exitosamente");
    }

    public GuiaResponseDTO subirGuiaS3(String id) throws IOException {
        GuiaDespacho guia = obtenerGuiaOFallar(id);

        if (guia.getRutaEfs() == null) {
            throw new IllegalStateException("La guía no tiene archivo en EFS para subir");
        }

        Path rutaEfs = Paths.get(guia.getRutaEfs());
        String claveS3 = s3Service.generarClaveS3(
                guia.getTransportista(),
                guia.getNumeroGuia() + ".pdf",
                guia.getFechaCreacion()
        );

        s3Service.subirArchivo(rutaEfs, claveS3);
        guia.setRutaS3(claveS3);
        guia.setEstado("SUBIDA_S3");
        guia.setFechaActualizacion(LocalDateTime.now());

        return mapToResponse(guia, "Guía subida a S3 correctamente");
    }

    public GuiaResponseDTO descargarGuia(String id, String transportistaSolicitante) {
        GuiaDespacho guia = obtenerGuiaOFallar(id);

        String transportistaNorm = transportistaSolicitante.toLowerCase()
                .replaceAll("[^a-z0-9]", "");

        boolean esAdmin = transportistaNorm.equals("admin");
        boolean esPropietario = guia.getTransportista()
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .equals(transportistaNorm);

        if (!esAdmin && !esPropietario) {
            throw new PermisoDenegadoException(
                    "El transportista '" + transportistaSolicitante +
                    "' no tiene permisos para descargar la guía " + guia.getNumeroGuia()
            );
        }

        if (guia.getRutaS3() == null) {
            throw new IllegalStateException("La guía aún no ha sido subida a S3");
        }

        String urlDescarga = s3Service.generarUrlDescarga(
                guia.getRutaS3(),
                Duration.ofHours(1)
        );
        guia.setUrlDescarga(urlDescarga);
        guia.setFechaActualizacion(LocalDateTime.now());

        return mapToResponse(guia, "URL de descarga generada (válida 1 hora)");
    }

    public GuiaResponseDTO actualizarGuia(String id, GuiaRequestDTO request) throws IOException {
        GuiaDespacho guia = obtenerGuiaOFallar(id);

        log.info("Actualizando guía: {}", guia.getNumeroGuia());

        guia.setTransportista(request.getTransportista());
        guia.setDestinatario(request.getDestinatario());
        guia.setDireccionDestino(request.getDireccionDestino());
        guia.setDescripcionCarga(request.getDescripcionCarga());
        guia.setPesoKg(request.getPesoKg());
        guia.setFechaActualizacion(LocalDateTime.now());
        guia.setEstado("ACTUALIZADA");

        if (guia.getRutaS3() != null) {
            s3Service.eliminarArchivo(guia.getRutaS3());
        }

        Path rutaEfs = generarPdfEnEfs(guia);
        guia.setRutaEfs(rutaEfs.toString());

        String claveS3 = s3Service.generarClaveS3(
                guia.getTransportista(),
                guia.getNumeroGuia() + ".pdf",
                guia.getFechaCreacion()
        );
        s3Service.subirArchivo(rutaEfs, claveS3);
        guia.setRutaS3(claveS3);
        guia.setEstado("SUBIDA_S3");

        return mapToResponse(guia, "Guía actualizada y re-subida a S3");
    }

    public void eliminarGuia(String id) {
        GuiaDespacho guia = obtenerGuiaOFallar(id);

        if (guia.getRutaS3() != null) {
            s3Service.eliminarArchivo(guia.getRutaS3());
        }

        if (guia.getRutaEfs() != null) {
            try {
                Files.deleteIfExists(Paths.get(guia.getRutaEfs()));
            } catch (IOException e) {
                log.warn("No se pudo eliminar el archivo EFS: {}", guia.getRutaEfs());
            }
        }

        guiasStore.remove(id);
        log.info("Guía eliminada: {}", guia.getNumeroGuia());
    }

    public List<GuiaResponseDTO> consultarPorTransportistaYFecha(
            String transportista, LocalDate fecha) {

        log.info("Consultando guías de {} para fecha {}", transportista, fecha);

        List<GuiaDespacho> guiasLocales = guiasStore.values().stream()
                .filter(g -> g.getTransportista().equalsIgnoreCase(transportista)
                        && g.getFechaCreacion().equals(fecha))
                .collect(Collectors.toList());

        List<String> clavesS3 = s3Service.listarGuias(transportista, fecha);
        log.info("Claves encontradas en S3: {}", clavesS3);

        return guiasLocales.stream()
                .map(g -> mapToResponse(g, null))
                .collect(Collectors.toList());
    }

    public GuiaResponseDTO obtenerGuia(String id) {
        return mapToResponse(obtenerGuiaOFallar(id), null);
    }

    private GuiaDespacho obtenerGuiaOFallar(String id) {
        GuiaDespacho guia = guiasStore.get(id);
        if (guia == null) {
            throw new GuiaNotFoundException("Guía no encontrada con ID: " + id);
        }
        return guia;
    }

    private Path generarPdfEnEfs(GuiaDespacho guia) throws IOException {
        Path dirEfs = Paths.get(efsMountPath);
        Files.createDirectories(dirEfs);

        Path archivoPdf = dirEfs.resolve(guia.getNumeroGuia() + ".pdf");

        try (PdfWriter writer = new PdfWriter(archivoPdf.toString());
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            document.add(new Paragraph("GUÍA DE DESPACHO").setBold().setFontSize(18));
            document.add(new Paragraph("Número: " + guia.getNumeroGuia()));
            document.add(new Paragraph("Transportista: " + guia.getTransportista()));
            document.add(new Paragraph("Destinatario: " + guia.getDestinatario()));
            document.add(new Paragraph("Dirección: " + guia.getDireccionDestino()));
            document.add(new Paragraph("Descripción: " + guia.getDescripcionCarga()));
            document.add(new Paragraph("Peso: " + guia.getPesoKg() + " kg"));
            document.add(new Paragraph("Fecha: " + guia.getFechaCreacion()));
            document.add(new Paragraph("Estado: " + guia.getEstado()));
        }

        log.info("PDF generado en EFS: {}", archivoPdf);
        return archivoPdf;
    }

    private GuiaResponseDTO mapToResponse(GuiaDespacho guia, String mensaje) {
        return GuiaResponseDTO.builder()
                .id(guia.getId())
                .numeroGuia(guia.getNumeroGuia())
                .transportista(guia.getTransportista())
                .destinatario(guia.getDestinatario())
                .direccionDestino(guia.getDireccionDestino())
                .descripcionCarga(guia.getDescripcionCarga())
                .pesoKg(guia.getPesoKg())
                .estado(guia.getEstado())
                .fechaCreacion(guia.getFechaCreacion())
                .fechaActualizacion(guia.getFechaActualizacion())
                .rutaS3(guia.getRutaS3())
                .urlDescarga(guia.getUrlDescarga())
                .mensaje(mensaje)
                .build();
    }
}