package com.unihub.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.unihub.backend.exception.FileStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Uploads a raw image byte array to Cloudinary and returns the secure_url.
     *
     * @param bytes    image content
     * @param folder   Cloudinary folder (e.g. "unihub/rooms")
     * @return secure HTTPS URL of the uploaded image
     */
    @SuppressWarnings("unchecked")
    public String uploadImage(byte[] bytes, String folder) {
        try {
            Map<String, Object> uploadResult = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "image"
            ));
            String url = (String) uploadResult.get("secure_url");
            if (url == null || url.isBlank()) {
                throw new FileStorageException("Cloudinary returned an empty URL");
            }
            log.info("Uploaded image to Cloudinary folder '{}': {}", folder, url);
            return url;
        } catch (IOException e) {
            throw new FileStorageException("Failed to upload image to Cloudinary: " + e.getMessage(), e);
        }
    }
}
