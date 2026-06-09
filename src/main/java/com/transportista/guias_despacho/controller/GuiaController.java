package com.transportista.guias_despacho.controller;

import com.transportista.guias_despacho.dto.GuiaRequestDTO;
import com.transportista.guias_despacho.dto.GuiaResponseDTO;
import com.transportista.guias_despacho.service.GuiaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/guias")
@RequiredArgsConstructor
public class GuiaController {

    private final GuiaService guiaService;

    @PostMapping
    public ResponseEntity<GuiaResponseDTO> crearGuia(
            @Valid @RequestBody GuiaRequestDTO request) throws IOException {
        log.info("POST /guias - Creando guía para: {}", request.getTransportista());
        return ResponseEntity.status(HttpStatus.CREATED).body(guiaService.crearGuia(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GuiaResponseDTO> obtenerGuia(@PathVariable String id) {
        log.info("GET /guias/{}", id);
        return ResponseEntity.ok(guiaService.obtenerGuia(id));
    }

    @PostMapping("/{id}/subir")
    public ResponseEntity<GuiaResponseDTO> subirGuiaS3(@PathVariable String id) throws IOException {
        log.info("POST /guias/{}/subir", id);
        return ResponseEntity.ok(guiaService.subirGuiaS3(id));
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<GuiaResponseDTO> descargarGuia(
            @PathVariable String id,
            @RequestParam String transportista) {
        log.info("GET /guias/{}/descargar - Solicitante: {}", id, transportista);
        return ResponseEntity.ok(guiaService.descargarGuia(id, transportista));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GuiaResponseDTO> actualizarGuia(
            @PathVariable String id,
            @Valid @RequestBody GuiaRequestDTO request) throws IOException {
        log.info("PUT /guias/{}", id);
        return ResponseEntity.ok(guiaService.actualizarGuia(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarGuia(@PathVariable String id) {
        log.info("DELETE /guias/{}", id);
        guiaService.eliminarGuia(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<GuiaResponseDTO>> buscarGuias(
            @RequestParam String transportista,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        log.info("GET /guias/buscar - transportista={}, fecha={}", transportista, fecha);
        return ResponseEntity.ok(guiaService.consultarPorTransportistaYFecha(transportista, fecha));
    }
}