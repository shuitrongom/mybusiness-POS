package com.mybusinesssilva.support.adapters.out.cloudinary;

import com.mybusinesssilva.support.config.CloudinaryProperties;
import com.mybusinesssilva.support.domain.model.SupportTicketImage;
import com.mybusinesssilva.support.domain.port.in.CreateSupportTicketUseCase.UploadedImage;
import com.mybusinesssilva.support.domain.port.out.ImageStoragePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Adaptador de almacenamiento de imágenes sobre <b>Cloudinary</b> con <b>upload firmado desde el
 * backend</b> ({@code POST https://api.cloudinary.com/v1_1/{cloud}/image/upload}) vía
 * {@link RestClient}, <b>sin SDK</b> (no se toca {@code pom.xml}). El {@code api_secret} nunca toca
 * el navegador y la firma SHA-1 se calcula con {@link MessageDigest} del JDK.
 *
 * <p>Degrada con elegancia: si {@code CLOUDINARY_URL} está vacío, {@link #isEnabled()} devuelve
 * {@code false}, {@link #upload(List)} devuelve lista vacía y se loggea {@code WARN} una sola vez.
 * Los fallos individuales de subida se omiten y se loggean; <b>nunca</b> se lanza una excepción (para
 * no tumbar la creación del ticket). Las imágenes llegan ya validadas por el servicio (magic bytes,
 * tamaño y cantidad).
 */
@Component
public class CloudinaryImageStorageAdapter implements ImageStoragePort {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorageAdapter.class);

    private final RestClient http;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final boolean enabled;

    /** Candado para loggear el aviso de "no configurado" una sola vez. */
    private volatile boolean disabledWarningLogged;

    public CloudinaryImageStorageAdapter(RestClient supportRestClient, CloudinaryProperties props) {
        this.http = supportRestClient;
        ParsedUrl parsed = parse(props == null ? null : props.url());
        this.cloudName = parsed.cloudName();
        this.apiKey = parsed.apiKey();
        this.apiSecret = parsed.apiSecret();
        this.enabled = parsed.isComplete();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<SupportTicketImage> upload(List<UploadedImage> images) {
        if (!enabled) {
            logDisabledOnce();
            return List.of();
        }
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<SupportTicketImage> result = new ArrayList<>(images.size());
        for (UploadedImage image : images) {
            SupportTicketImage uploaded = uploadOne(image);
            if (uploaded != null) {
                result.add(uploaded);
            }
        }
        return result;
    }

    /** Sube una imagen; ante cualquier fallo lo loggea y devuelve {@code null} (nunca lanza). */
    private SupportTicketImage uploadOne(UploadedImage image) {
        if (image == null || image.bytes() == null || image.bytes().length == 0) {
            return null;
        }
        try {
            long timestamp = System.currentTimeMillis() / 1000L;
            String folder = "support/" + cloudName; // carpeta estable del buzón; el businessId lo
            // acota la capa de aplicación. Se usa el cloud como namespace neutro para no exponer el id
            // interno en la URL firmada.

            // Parámetros firmables (todos menos file, api_key y signature), ordenados alfabéticamente.
            TreeMap<String, String> signable = new TreeMap<>();
            signable.put("folder", folder);
            signable.put("timestamp", String.valueOf(timestamp));
            String signature = sign(signable, apiSecret);

            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", fileResource(image));
            form.add("api_key", apiKey);
            form.add("timestamp", String.valueOf(timestamp));
            form.add("folder", folder);
            form.add("signature", signature);

            CloudinaryUploadResponse response = http.post()
                    .uri("https://api.cloudinary.com/v1_1/{cloud}/image/upload", cloudName)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(CloudinaryUploadResponse.class);

            if (response == null || response.secureUrl() == null || response.publicId() == null) {
                log.warn("[CLOUDINARY] Respuesta de subida incompleta; imagen omitida.");
                return null;
            }
            return new SupportTicketImage(
                    response.secureUrl(), response.publicId(),
                    response.width(), response.height(), response.bytes());
        } catch (RuntimeException ex) {
            // Un fallo de red/proveedor en UNA imagen no tumba el ticket: se omite y se loggea.
            log.warn("[CLOUDINARY] Fallo al subir una imagen (se omite). Causa: {}",
                    ex.getClass().getSimpleName());
            return null;
        }
    }

    private static ByteArrayResource fileResource(UploadedImage image) {
        final String filename = image.originalFilename() == null || image.originalFilename().isBlank()
                ? "upload" : image.originalFilename();
        return new ByteArrayResource(image.bytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    /** Firma Cloudinary: SHA1( "k1=v1&k2=v2..." (orden alfabético) + api_secret ), en hex. */
    private static String sign(Map<String, String> signableParams, String apiSecret) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : signableParams.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        sb.append(apiSecret);
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            // SHA-1 es parte del JDK; este caso no ocurre en la práctica.
            throw new IllegalStateException("SHA-1 no disponible en el JDK", ex);
        }
    }

    private void logDisabledOnce() {
        if (!disabledWarningLogged) {
            disabledWarningLogged = true;
            log.warn("[CLOUDINARY] CLOUDINARY_URL ausente; subida de imágenes deshabilitada "
                    + "(los tickets se guardan sin imágenes).");
        }
    }

    /** Parsea {@code cloudinary://api_key:api_secret@cloud_name}; vacío/invalido => no habilitado. */
    private static ParsedUrl parse(String url) {
        if (url == null || url.isBlank()) {
            return ParsedUrl.empty();
        }
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            String userInfo = uri.getUserInfo();   // api_key:api_secret
            String host = uri.getHost();            // cloud_name
            if (userInfo == null || host == null || host.isBlank()) {
                return ParsedUrl.empty();
            }
            int colon = userInfo.indexOf(':');
            if (colon <= 0 || colon >= userInfo.length() - 1) {
                return ParsedUrl.empty();
            }
            String key = userInfo.substring(0, colon);
            String secret = userInfo.substring(colon + 1);
            if (key.isBlank() || secret.isBlank()) {
                return ParsedUrl.empty();
            }
            return new ParsedUrl(host, key, secret);
        } catch (RuntimeException ex) {
            return ParsedUrl.empty();
        }
    }

    /** Datos parseados de {@code CLOUDINARY_URL}. */
    private record ParsedUrl(String cloudName, String apiKey, String apiSecret) {
        static ParsedUrl empty() {
            return new ParsedUrl(null, null, null);
        }

        boolean isComplete() {
            return cloudName != null && apiKey != null && apiSecret != null;
        }
    }

    /** Proyección de la respuesta JSON de Cloudinary (solo los campos que usamos). */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record CloudinaryUploadResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("secure_url") String secureUrl,
            @com.fasterxml.jackson.annotation.JsonProperty("public_id") String publicId,
            @com.fasterxml.jackson.annotation.JsonProperty("width") Integer width,
            @com.fasterxml.jackson.annotation.JsonProperty("height") Integer height,
            @com.fasterxml.jackson.annotation.JsonProperty("bytes") Long bytes) {
    }
}
