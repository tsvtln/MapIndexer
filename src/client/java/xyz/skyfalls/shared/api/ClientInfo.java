package xyz.skyfalls.shared.api;

import java.util.UUID;

import xyz.skyfalls.shared.abstraction.FreeBlockPos;

public record ClientInfo(String version, boolean isOnServer, 
		String playerName, UUID playerUUID, 
		FreeBlockPos position, String dimension) {
}
