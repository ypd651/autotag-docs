package com.autotag.docs.service;

import com.autotag.docs.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final StorageProperties storageProperties;
    private Path rootLocation;

    public FileStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
            log.info("Directorio de almacenamiento inicializado en: {}", this.rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo inicializar el directorio de almacenamiento", e);
        }
    }

    public Path store(MultipartFile file, UUID documentId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("No se puede almacenar un archivo vacío.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = ".pdf";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String targetFileName = documentId.toString() + extension;
        Path destinationFile = this.rootLocation.resolve(targetFileName).normalize().toAbsolutePath();

        if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
            throw new SecurityException("No se puede almacenar el archivo fuera del directorio configurado.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Archivo guardado con éxito: {}", destinationFile);
            return destinationFile;
        } catch (IOException e) {
            log.error("Fallo al almacenar el archivo {}", targetFileName, e);
            throw new RuntimeException("Fallo al almacenar el archivo", e);
        }
    }

    public Resource loadAsResource(String filename) {
        try {
            Path file = rootLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("No se pudo leer el archivo: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error en URL del archivo: " + filename, e);
        }
    }

    public boolean delete(String filename) {
        try {
            Path file = rootLocation.resolve(filename).normalize();
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("No se pudo eliminar el archivo físico: {}", filename, e);
            return false;
        }
    }

    public Path getRootLocation() {
        return rootLocation;
    }
}
