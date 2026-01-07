package com.example.fixclient.service;

import com.example.fixclient.util.EMXSigner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.field.SenderCompID;
import quickfix.field.TargetCompID;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class CertificateService {

    private final Map<String, String> certCache = new ConcurrentHashMap<>();
    private final Map<String, PrivateKey> privateKeyCache = new ConcurrentHashMap<>();
    private final ConfigService configService;

    /**
     * Returns the Base64-encoded certificate for the given senderCompId.
     * Loads from disk on first access, then caches for subsequent calls.
     */
    public String getCertificateBase64(String senderCompId) {
        return certCache.computeIfAbsent(senderCompId, this::loadAndEncode);
    }

    public String signMessage(Message message) {
        try {
            String senderCompId = message.getHeader().getString(SenderCompID.FIELD);

            // Check caches first
            String certBase64 = getCertificateBase64(senderCompId);
            PrivateKey privateKey = getPrivateKey(senderCompId);

            if (certBase64 == null || privateKey == null) {
                return null;
            }

            // Decode cert from cache (it's stored as base64 string in cache per existing
            // design,
            // but we need X509Certificate object.
            // Optimization: We could cache X509Certificate object directly instead of
            // Base64 string,
            // but strict requirement was "p12 key also in ConcurrentHashMap".
            // Let's decode the cached base64 back to X509 for signing.
            // Or better: update certCache to store X509Certificate?
            // The method getCertificateBase64 is public and returns String.
            // Let's keep it simple: decode the cached string.

            byte[] decoded = Base64.getDecoder().decode(certBase64);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(decoded));

            return EMXSigner.signMessageWithPrivateAndPublicKey(cert, privateKey);

        } catch (FieldNotFound e) {
            log.error("Could not find SenderCompID in message header", e);
        } catch (Exception e) {
            log.error("Error signing message", e);
        }
        return null;
    }

    private PrivateKey getPrivateKey(String senderCompId) {
        return privateKeyCache.computeIfAbsent(senderCompId, this::loadPrivateKey);
    }

    private PrivateKey loadPrivateKey(String senderCompId) {
        String password = configService.findPassword(senderCompId);
        if (password == null) {
            log.warn("No password found for senderCompId: {}", senderCompId);
            return null;
        }

        Path keyPath = Path.of("certs", senderCompId + ".p12");
        if (!keyPath.toFile().exists()) {
            log.warn("Key file not found for {}", senderCompId);
            return null;
        }

        try (FileInputStream fis = new FileInputStream(keyPath.toFile())) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(fis, password.toCharArray());
            Enumeration<String> aliases = ks.aliases();
            if (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                log.info("Loaded and cached private key for senderCompId: {}", senderCompId);
                return (PrivateKey) ks.getKey(alias, password.toCharArray());
            }
        } catch (Exception e) {
            log.error("Failed to load private key for {}", senderCompId, e);
        }
        return null;
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
