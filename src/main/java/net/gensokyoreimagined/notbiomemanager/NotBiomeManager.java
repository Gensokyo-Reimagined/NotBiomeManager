package net.gensokyoreimagined.notbiomemanager;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import org.apache.commons.io.FileUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class NotBiomeManager extends JavaPlugin {

    public static Logger logger;

    private CommentedConfigurationNode configNode;
    private ConfigurationLoader<CommentedConfigurationNode> loader;

    private static Field specialEffectsField;

    static {
        try {
            specialEffectsField = Biome.class.getDeclaredField("specialEffects");
            specialEffectsField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private CommentedConfigurationNode defaultConfig(ConfigurationLoader<@NotNull CommentedConfigurationNode> loader) {
        CommentedConfigurationNode defaultConfig = loader.createNode();
        try {
            defaultConfig.node("configkey").set("configvalue").comment("placeholdercomment");
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
        return defaultConfig;
    }

    YamlConfigurationLoader.Builder builder = YamlConfigurationLoader.builder().indent(4).nodeStyle(NodeStyle.BLOCK);

    Map<NamespacedKey, Path> biomeConfigurationFiles = new HashMap<>();

    Path biomesFolder = Paths.get(getDataFolder().getPath(), "biomes");

    @Override
    public void onLoad() {
        logger = getLogger();

        File configFile = new File(getDataFolder(), "config.conf");
        ConfigurationOptions configurationOptions = ConfigurationOptions.defaults();
        loader = HoconConfigurationLoader.builder()
                .file(configFile)
                .defaultOptions(configurationOptions)
                .build();

        try {
            if (!configFile.exists()) {
                loader.save(defaultConfig(loader));
            }
            configNode = loader.load();
            configNode.mergeFrom(defaultConfig(loader));
            loader.save(configNode);
        } catch (ConfigurateException e) {
            throw new RuntimeException(e);
        }
        biomesFolder.toFile().mkdirs();

        Map<NamespacedKey, Biome> toAddBiomesMap = new HashMap<>();
        FileUtils.iterateFiles(biomesFolder.toFile(), new String[] { "yml", "yaml" }, true).forEachRemaining(file -> {
            // logger.info("Loading biome file " + file.getName()); // REMOVED
            CommentedConfigurationNode node = null;
            try {
                var loader = YamlConfigurationLoader.builder()
                        .file(file)
                        .defaultOptions(configurationOptions)
                        .build();
                node = loader.load();
            } catch (Exception e) {
                e.printStackTrace();
                logger.severe("Failed to load biome file " + file.getName() + ", stopping server.");
                getServer().shutdown();
            }
            if (node == null) {
                return;
            }

            try {
                // Delegate to BiomeConfigLoader
                Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME)
                        .orElseThrow();
                Pair<NamespacedKey, Biome> result = BiomeConfigLoader.loadBiome(node, biomes);

                toAddBiomesMap.put(result.first(), result.second());
                biomeConfigurationFiles.put(result.first(), file.toPath());
            } catch (Exception e) {
                e.printStackTrace();
                logger.severe("Failed to parse biome file " + file.getName() + " ATTEMPTING TO REGISTER PLACEHOLDER");
                try {
                    NamespacedKey key = NamespacedKey.fromString(node.node("Key").getString().toLowerCase());
                    Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME)
                            .orElseThrow();
                    Biome base = BiomeRegistryHelper.getBiome(biomes, NamespacedKey.minecraft("plains"));
                    toAddBiomesMap.put(key, base);
                } catch (Exception e2) {
                    logger.severe("Failed to register placeholder for " + file.getName() + ", stopping server.");
                    getServer().shutdown();
                }
            }
        });

        logger.info("Loaded " + toAddBiomesMap.size() + " biomes.");

        for (var entry : toAddBiomesMap.entrySet()) {
            try {
                Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME)
                        .orElseThrow();

                net.minecraft.resources.ResourceKey<Biome> resourceKey = BiomeRegistryHelper
                        .createResourceKey(entry.getKey());

                BiomeRegistryHelper.registerBiome(resourceKey, entry.getValue(), biomes);
            } catch (Exception e) {
                e.printStackTrace();
                logger.severe("Failed to register biome " + entry.getKey() + ", stopping server.");
                getServer().shutdown();
            }
        }
    }

    public Path getBiomeFile(NamespacedKey key) {
        return biomeConfigurationFiles.get(key);
    }

    public java.util.Set<NamespacedKey> getLoadedBiomeKeys() {
        return biomeConfigurationFiles.keySet();
    }

    @Override
    public void onEnable() {
        BiomeCommands.register(this);
    }

    public void loadConfig(ConfigurationNode node) {
        // Empty if handled elsewhere
    }

    private boolean actuallyHasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }

    public void reloadAllBiomes() {
        biomeConfigurationFiles.keySet().forEach(this::reloadBiome);
        logger.info("All biomes reloaded.");
    }

    public void reloadBiome(NamespacedKey key) {
        Path path = biomeConfigurationFiles.get(key);
        if (path == null) {
            // Try to find it if it was just created
            path = biomesFolder.resolve(key.getKey() + ".yml");
            if (path.toFile().exists()) {
                biomeConfigurationFiles.put(key, path);
            } else {
                return;
            }
        }

        try {
            var loader = YamlConfigurationLoader.builder()
                    .path(path)
                    .defaultOptions(ConfigurationOptions.defaults())
                    .build();
            CommentedConfigurationNode node = loader.load();

            Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME)
                    .orElseThrow();

            Pair<NamespacedKey, Biome> result = BiomeConfigLoader.loadBiome(node, biomes);
            Biome newBiomeData = result.right();

            Biome existingBiome = BiomeRegistryHelper.getBiome(biomes, key);

            if (existingBiome != null) {
                specialEffectsField.set(existingBiome, newBiomeData.getSpecialEffects());

                try {
                    Field attributesField = Biome.class.getDeclaredField("attributes");
                    attributesField.setAccessible(true);
                    attributesField.set(existingBiome, attributesField.get(newBiomeData));
                } catch (NoSuchFieldException e) {
                    // i love glue
                }

                logger.info("Reloaded biome " + key);
            } else {
                logger.warning("Could not find existing biome to update for " + key);
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.severe("Failed to reload biome " + key);
        }
    }
}
