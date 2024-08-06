package xyz.skyfalls.shared.utils;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

public class RegistryUtils {
    public static <T> String toString(RegistryEntry<T> entry) {
        return entry.getKey().get().getValue().getPath();
    }
    public static <T> String toString(RegistryKey<T> key) {
        return key.getValue().getPath();
    }
}
