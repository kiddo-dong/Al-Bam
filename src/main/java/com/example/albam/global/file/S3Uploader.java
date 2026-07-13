package com.example.albam.global.file;

import com.example.albam.global.exception.InvalidRequestException;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
public class S3Uploader {

    private static final String AMAZON_S3_HOST = ".amazonaws.com/";

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    public String upload(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("업로드할 파일이 비어 있습니다.");
        }
        String key = directory + "/" + UUID.randomUUID() + "-" + sanitizeFilename(file.getOriginalFilename());
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new InvalidRequestException("파일 업로드에 실패했습니다.");
        }
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucketName, region, key);
    }

    public void delete(String fileUrl) {
        if (fileUrl == null) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(extractKey(fileUrl))
                .build());
    }

    private String extractKey(String fileUrl) {
        int index = fileUrl.indexOf(AMAZON_S3_HOST);
        return fileUrl.substring(index + AMAZON_S3_HOST.length());
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null) {
            return "file";
        }
        return originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
