package com.ikae.snowthing.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.global.config.BotProperties;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@Service
public class BotDelegationTokenService {

    static final String ISSUER = "snowthing-backend";
    static final String AUDIENCE = "snowthing-mcp";
    static final String READ_SCOPE = "snowthing:read";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final long CLOCK_SKEW_SECONDS = 5L;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final BotProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public BotDelegationTokenService(BotProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    BotDelegationTokenService(BotProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String issue(String memberPublicId, String chatMessageId) {
        if (memberPublicId == null
                || memberPublicId.isBlank()
                || chatMessageId == null
                || chatMessageId.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        byte[] secret = signingSecret();
        Instant issuedAt = clock.instant();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("aud", AUDIENCE);
        claims.put("sub", memberPublicId);
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", issuedAt.plus(properties.delegationTtl()).getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("chatMessageId", chatMessageId);
        claims.put("scopes", List.of(READ_SCOPE));
        return encode(Map.of("alg", "HS256", "typ", "JWT"), claims, secret);
    }

    public BotDelegationClaims validate(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) throw invalidCredentials();
            byte[] expected = sign(parts[0] + "." + parts[1], signingSecret());
            byte[] actual = DECODER.decode(parts[2]);
            if (!java.security.MessageDigest.isEqual(expected, actual)) {
                throw invalidCredentials();
            }

            Map<String, Object> header = decodeJson(parts[0]);
            Map<String, Object> claims = decodeJson(parts[1]);
            Instant now = clock.instant();
            long issuedAt = epochSecond(claims.get("iat"));
            long expiresAt = epochSecond(claims.get("exp"));
            long maximumLifetime =
                    properties.delegationTtl().plusSeconds(CLOCK_SKEW_SECONDS).toSeconds();
            List<String> scopes = stringList(claims.get("scopes"));

            if (!"HS256".equals(header.get("alg"))
                    || !"JWT".equals(header.get("typ"))
                    || !ISSUER.equals(claims.get("iss"))
                    || !AUDIENCE.equals(claims.get("aud"))
                    || !scopes.contains(READ_SCOPE)
                    || issuedAt <= 0
                    || issuedAt > now.plusSeconds(CLOCK_SKEW_SECONDS).getEpochSecond()
                    || expiresAt <= now.getEpochSecond()
                    || expiresAt <= issuedAt
                    || expiresAt - issuedAt > maximumLifetime) {
                throw invalidCredentials();
            }

            return new BotDelegationClaims(
                    requiredString(claims.get("sub")),
                    requiredString(claims.get("jti")),
                    requiredString(claims.get("chatMessageId")),
                    Instant.ofEpochSecond(issuedAt),
                    Instant.ofEpochSecond(expiresAt),
                    scopes);
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidCredentials();
        }
    }

    private String encode(Map<String, Object> header, Map<String, Object> claims, byte[] secret) {
        try {
            String unsigned = encodeJson(header) + "." + encodeJson(claims);
            return unsigned + "." + ENCODER.encodeToString(sign(unsigned, secret));
        } catch (Exception exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String encodeJson(Map<String, Object> value) throws Exception {
        return ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private Map<String, Object> decodeJson(String value) throws Exception {
        return objectMapper.readValue(DECODER.decode(value), new TypeReference<>() {});
    }

    private byte[] sign(String value, byte[] secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] signingSecret() {
        byte[] secret = properties.delegationSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return secret;
    }

    private String requiredString(Object value) {
        if (value instanceof String string && !string.isBlank()) return string;
        throw invalidCredentials();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) throw invalidCredentials();
        List<String> result = new ArrayList<>(values.size());
        for (Object element : values) {
            if (!(element instanceof String string) || string.isBlank()) throw invalidCredentials();
            result.add(string);
        }
        return List.copyOf(result);
    }

    private long epochSecond(Object value) {
        return value instanceof Number number ? number.longValue() : -1L;
    }

    private CustomException invalidCredentials() {
        return new CustomException(ErrorCode.INVALID_CREDENTIALS);
    }
}
