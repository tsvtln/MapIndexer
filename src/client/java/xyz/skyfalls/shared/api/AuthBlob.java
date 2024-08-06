package xyz.skyfalls.shared.api;

import lombok.AllArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
public class AuthBlob {
    private String username;
    private UUID uuid;

    private String nonce;

    private String version;
}
