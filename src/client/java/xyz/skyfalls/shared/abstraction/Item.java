package xyz.skyfalls.shared.abstraction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.yggdrasil.response.MinecraftTexturesPayload;
import com.mojang.util.UUIDTypeAdapter;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;
import xyz.skyfalls.shared.utils.RegistryUtils;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@EqualsAndHashCode
public class Item {
    final static String TYPE_S26_COIN = "server26_lc_coin";
    final static String KEY_ENCHANTMENTS = "Enchantments";
    final static String KEY_ENCHANTMENTS_BOOK = "StoredEnchantments";
    String type;
    int count;
    @Nullable
    Map<String, Integer> enchantments;
    @Nullable
    String headSkinHash;
    @Nullable
    String potionType;
    @Nullable
    Integer mapId;

    private final static Gson gsonWithDashlessUUID = new GsonBuilder()
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .create();

    public Item(ItemStack stack) {
        this.type = stack.getItem().getRegistryEntry().getKey().get().getValue().getPath();
        this.count = stack.getCount();
        NbtCompound nbt = stack.getTag() != null ? stack.getTag() : new NbtCompound();

        NbtList enchants = null;
        if (nbt != null) {
            var lodestonePos = nbt.getCompound("LodestonePos");
            if (!lodestonePos.isEmpty() &&
                    nbt.getString("LodestoneDimension").equals("minecraft:overworld") &&
                    lodestonePos.getInt("X") == 0 &&
                    lodestonePos.getInt("Y") == 108 &&
                    lodestonePos.getInt("Z") == 0) {
                this.type = TYPE_S26_COIN;
            }

            if (nbt.contains(KEY_ENCHANTMENTS)) {
                enchants = nbt.getList(KEY_ENCHANTMENTS, NbtElement.COMPOUND_TYPE);
            } else if (nbt.contains(KEY_ENCHANTMENTS_BOOK)) {
                enchants = nbt.getList(KEY_ENCHANTMENTS_BOOK, NbtElement.COMPOUND_TYPE);
            }
            if (enchants != null) {
                this.enchantments = new HashMap<>();
                EnchantmentHelper.deserialize(enchants).entrySet().forEach(e -> {
                    this.enchantments.put(RegistryUtils.toString(e.getKey().getRegistryEntry()), e.getValue());
                });
            }

            String texture = nbt.getCompound("SkullOwner")
                    .getCompound("Properties")
                    .getList("textures", NbtElement.COMPOUND_TYPE)
                    .getCompound(0)
                    .getString("Value");
            if (!texture.isEmpty()) {
                var payload = gsonWithDashlessUUID.fromJson(new String(Base64.getDecoder().decode(texture)), MinecraftTexturesPayload.class);
                var url = payload.textures().get(MinecraftProfileTexture.Type.SKIN).getUrl();
                if (url != null) {
                    headSkinHash = "v2:" + url.replaceFirst("^http://textures.minecraft.net/texture/", "");
                }
            }

            String potionType = nbt.getString("Potion").replaceFirst("^minecraft:", "");
            if (!potionType.isEmpty()) {
                this.potionType = potionType;
            }

            if (nbt.contains("map")) {
                this.mapId = nbt.getInt("map");
            }
        }
    }
}
