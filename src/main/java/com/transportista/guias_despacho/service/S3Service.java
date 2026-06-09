package com.transportista.guias_despacho.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String generarClaveS3(String transportista, String nombreArchivo, LocalDate fecha) {
        String fechaStr = fecha.format(DATE_FORMAT);
        String transportistaNorm = transportista.toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        return String.format("%s/%s/%s", fechaStr, transportistaNorm, nombreArchivo);
    }

    public String subirArchivo(Path rutaLocal, String claveS3) throws IOException {
        log.info("Subiendo archivo a S3: bucket={}, key={}", bucketName, claveS3);

        byte[] contenido = Files.readAllBytes(rutaLocal);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(claveS3)
                .contentType("application/pdf")
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(contenido));
        log.info("Archivo subido exitosamente: {}", claveS3);
        return claveS3;
    }

    public String generarUrlDescarga(String claveS3, Duration duracion) {
        log.info("Generando URL prefirmada para: {}", claveS3);

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duracion)
                .getObjectRequest(r -> r
                        .bucket(bucketName)
                        .key(claveS3))
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }

    public void descargarArchivo(String claveS3, Path destino) throws IOException {
        log.info("Descargando archivo de S3: {}", claveS3);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(claveS3)
                .build();

        byte[] contenido = s3Client.getObjectAsBytes(request).asByteArray();
        Files.write(destino, contenido);
        log.info("Archivo descargado en: {}", destino);
    }

    public void eliminarArchivo(String claveS3) {
        log.info("Eliminando archivo de S3: {}", claveS3);

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(claveS3)
                .build();

        s3Client.deleteObject(request);
        log.info("Archivo eliminado: {}", claveS3);
    }

    public List<String> listarGuias(String transportista, LocalDate fecha) {
        String fechaStr = fecha.format(DATE_FORMAT);
        String transportistaNorm = transportista.toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        String prefijo = String.format("%s/%s/", fechaStr, transportistaNorm);

        log.info("Listando guías con prefijo: {}", prefijo);

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefijo)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        return response.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toList());
    }

    public boolean existeArchivo(String claveS3) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(claveS3)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}