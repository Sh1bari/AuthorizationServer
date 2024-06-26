package ru.timetracker.authorizationserver.utils;

import io.jsonwebtoken.*;
import lombok.*;
import ru.timetracker.authorizationserver.exceptions.WrongTokenExc;
import ru.timetracker.authorizationserver.models.entities.Role;
import ru.timetracker.authorizationserver.models.entities.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.stream.Collectors;

public class JwtUtil {

    private static PrivateKey privateKey;
    private static PublicKey publicKey;

    static {
        try {
            // Чтение закрытого ключа из файла
            byte[] privateKeyBytes = Files.readAllBytes(Paths.get("private_key.pem"));
            String privateKeyPEM = new String(privateKeyBytes);
            privateKeyPEM = privateKeyPEM.replace("-----BEGIN PRIVATE KEY-----", "");
            privateKeyPEM = privateKeyPEM.replace("-----END PRIVATE KEY-----", "");
            privateKeyPEM = privateKeyPEM.replaceAll("\\s+", "");

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyPEM));
            privateKey = keyFactory.generatePrivate(privateKeySpec);

            // Чтение открытого ключа из файла
            byte[] publicKeyBytes = Files.readAllBytes(Paths.get("public_key.pem"));
            String publicKeyPEM = new String(publicKeyBytes);
            publicKeyPEM = publicKeyPEM.replace("-----BEGIN PUBLIC KEY-----", "");
            publicKeyPEM = publicKeyPEM.replace("-----END PUBLIC KEY-----", "");
            publicKeyPEM = publicKeyPEM.replaceAll("\\s+", "");

            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyPEM));
            publicKey = keyFactory.generatePublic(publicKeySpec);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
    }

    public static String generateAccessToken(User user) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        long expMillis = nowMillis + 2 * 24 * 60 * 60 * 1000; // 2 дня в миллисекундах
        Date exp = new Date(expMillis);
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .setClaims(generateAccessClaims(user))
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(SignatureAlgorithm.RS256, privateKey)
                .compact();
    }
    private static Map<String, Object> generateAccessClaims(User user){
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", user.getId().toString());
        claims.put("tokenType", "access");
        claims.put("roles", user.getRoles().stream().map(Role::getName).toList());
        return claims;
    }

    public static String generateRefreshToken(User user) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        long expMillis = nowMillis + 20 * 24 * 60 * 60 * 1000; // 20 дня в миллисекундах
        Date exp = new Date(expMillis);
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .setClaims(generateRefreshClaims(user))
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(SignatureAlgorithm.RS256, privateKey)
                .compact();
    }
    private static Map<String, Object> generateRefreshClaims(User user){
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "refresh");
        claims.put("sub", user.getId().toString());
        return claims;
    }

    public static Jws<Claims> getClaims(String token) {
        try {
            Jws<Claims> claimsJws = Jwts.parser()
                    .setSigningKey(publicKey)
                    .parseClaimsJws(token);
            return claimsJws;
        } catch (JwtException | IllegalArgumentException e) {
            throw new WrongTokenExc();
        }
    }
}
