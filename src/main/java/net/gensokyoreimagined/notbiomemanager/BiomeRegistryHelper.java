package net.gensokyoreimagined.notbiomemanager;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import org.bukkit.NamespacedKey;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Logger;

public class BiomeRegistryHelper {
    private static final Logger logger = Logger.getLogger("NotBiomeManager");

    public static void registerBiome(ResourceKey<Biome> key, Biome biome, Registry<Biome> registry) {
        if (!(registry instanceof WritableRegistry<Biome> writable)) {
            logger.severe("Registry is not writable! Cannot register biome " + key);
            return;
        }

        // Check for duplicates/existing holders
        if (registry.containsKey(key)) {
            if (registry.get(key) != null) {
                logger.warning("Biome " + key + " is already registered and bound, skipping.");
                return;
            }
            logger.info("Biome " + key + " exists but is unbound. Proceeding to bind.");
        }

        unfreeze(registry);

        logger.info("Registering biome: " + key);
        writable.register(key, biome, RegistrationInfo.BUILT_IN);

        ensureBound(registry, key, biome);

        freeze(registry);
    }

    public static ResourceKey<Biome> createResourceKey(NamespacedKey key) {
        try {
            Object nmsKey = getNMSKey(key);
            // ResourceKey.create(RegistryKey, ResourceLocation)
            // We use reflection to invoke it
            java.lang.reflect.Method createMethod = ResourceKey.class.getMethod("create", ResourceKey.class,
                    nmsKey.getClass());
            return (ResourceKey<Biome>) createMethod.invoke(null, net.minecraft.core.registries.Registries.BIOME,
                    nmsKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ResourceKey", e);
        }
    }

    private static Object getNMSKey(NamespacedKey key) {
        try {
            Class<?> craftNamespacedKeyClass = Class.forName("org.bukkit.craftbukkit.util.CraftNamespacedKey");
            Method method = craftNamespacedKeyClass.getMethod("toMinecraft", NamespacedKey.class);
            return method.invoke(null, key);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get NMS key via reflection", e);
        }
    }

    private static void ensureBound(Registry<Biome> registry, ResourceKey<Biome> key, Biome biome) {
        try {
            var opt = registry.get(key);
            if (opt instanceof Optional<?> o && o.isPresent()) {
                Object holder = o.get();
                if (holder instanceof Holder.Reference<?> ref) {
                    if (!ref.isBound()) {
                        logger.warning("Biome " + key + " is still unbound after registration! Attempting FORCE BIND.");
                        forceBind(ref, biome);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void forceBind(Holder.Reference<?> ref, Biome biome) {
        try {
            Method bindMethod = Holder.Reference.class.getDeclaredMethod("bindValue", Object.class);
            bindMethod.setAccessible(true);
            bindMethod.invoke(ref, biome);
            logger.info("Force bind successful.");
        } catch (Exception e) {
            logger.severe("Failed to force bind: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Biome getBiome(Registry<Biome> registry, NamespacedKey key) {
        try {
            Object nmsKey = getNMSKey(key);
            Method getMethod = registry.getClass().getMethod("get", nmsKey.getClass());
            Object result = getMethod.invoke(registry, nmsKey);

            if (result instanceof Optional opt && opt.isPresent()) {
                Object ref = opt.get();
                // ref is Reference/Holder
                Method valueMethod = ref.getClass().getMethod("value");
                return (Biome) valueMethod.invoke(ref);
            }
        } catch (Exception e) {
            logger.warning("Failed to get biome " + key + ": " + e.getMessage());
        }
        return null;
    }

    public static net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> getSound(NamespacedKey key) {
        try {
            Object nmsKey = getNMSKey(key);
            // BuiltInRegistries.SOUND_EVENT
            Registry<net.minecraft.sounds.SoundEvent> registry = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT;
            Method getMethod = registry.getClass().getMethod("get", nmsKey.getClass());
            Object result = getMethod.invoke(registry, nmsKey);

            if (result instanceof Optional opt && opt.isPresent()) {
                return (net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent>) opt.orElse(null);
            }
        } catch (Exception e) {
            logger.warning("Failed to get sound " + key + ": " + e.getMessage());
        }
        return null;
    }

    private static void unfreeze(Registry<?> registry) {
        if (registry instanceof MappedRegistry<?> mappedRegistry) {
            try {
                Field frozen = MappedRegistry.class.getDeclaredField("frozen");
                frozen.setAccessible(true);
                frozen.set(mappedRegistry, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void freeze(Registry<?> registry) {
        if (registry instanceof MappedRegistry<?> mappedRegistry) {
            try {
                Field frozen = MappedRegistry.class.getDeclaredField("frozen");
                frozen.setAccessible(true);
                frozen.set(mappedRegistry, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
