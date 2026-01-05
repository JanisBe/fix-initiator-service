package com.example.fixclient.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);
    private final Map<String, String> certCache = new ConcurrentHashMap<>();

    /**
     * Returns the Base64-encoded certificate for the given senderCompId.
     * Loads from disk on first access, then caches for subsequent calls.
     */
    public String getCertificateBase64(String senderCompId) {
        return certCache.computeIfAbsent(senderCompId, this::loadAndEncode);
    }

    private String loadAndEncode(String senderCompId) {
        Path certPath = Path.of("certs", senderCompId + ".cer");
        if (!certPath.toFile().exists()) {
            log.warn("Certificate file not found: {}", certPath);
            return null;
        }
        try (FileInputStream fis = new FileInputStream(certPath.toFile())) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
            byte[] encoded = cert.getEncoded();
            log.info("Loaded and cached certificate for senderCompId: {}", senderCompId);
            return Base64.getEncoder().encodeToString(encoded);
        } catch (Exception e) {
            log.error("Failed to load certificate for {}", senderCompId, e);
            return null;
        }
    }
}
