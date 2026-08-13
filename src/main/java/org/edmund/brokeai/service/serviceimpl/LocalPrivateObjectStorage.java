package org.edmund.brokeai.service.serviceimpl;

import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.service.PrivateObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class LocalPrivateObjectStorage implements PrivateObjectStorage {
    private final Path root;

    public LocalPrivateObjectStorage(
        @Value("${app.object-storage.directory:${java.io.tmpdir}/broke-ai-private-objects}") String directory
    ) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void put(String objectKey, byte[] content) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED", "The file could not be stored.");
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try {
            return Files.readAllBytes(resolve(objectKey));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The stored file was not found.");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED", "The file could not be removed.");
        }
    }

    private Path resolve(String objectKey) {
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The object key is invalid.");
        }
        return resolved;
    }
}
