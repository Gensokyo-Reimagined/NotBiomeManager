package net.gensokyoreimagined.notbiomemanager;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class BiomeCommands {

    private static final String[] ALL_FIELDS = new String[] {
            "Fog_Color", "Water_Color", "Water_Fog_Color", "Sky_Color",
            "Grass_Modifier", "Foliage_Color", "Grass_Color", "Dry_Foliage_Color",
            "Particle.Type", "Particle.Density",
            "Cave_Sound.Sound", "Cave_Sound.Tick_Delay", "Cave_Sound.Search_Distance", "Cave_Sound.Sound_Offset",
            "Random_Sound.Sound", "Random_Sound.Tick_Chance",
            "Music.Sound", "Music.Min_Delay", "Music.Max_Delay", "Music.Override_Previous_Music"
    };

    public static void register(JavaPlugin plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            registerCommands(event, plugin);
        });
    }

    private static void registerCommands(ReloadableRegistrarEvent<Commands> event, JavaPlugin plugin) {
        var commands = event.registrar();
        var mainCommand = Commands.literal("notbiomemanager")
                .requires(source -> source.getSender().hasPermission("notbiomemanager.admin"))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage("Reloading NotBiomeManager configuration...");
                            if (plugin instanceof NotBiomeManager manager) {
                                manager.reloadAllBiomes();
                                ctx.getSource().getSender().sendMessage("Biomes reloaded. Relog to see changes.");
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("create")
                        .then(Commands.argument("base", ArgumentTypes.namespacedKey())
                                .suggests((ctx, builder) -> {
                                    net.minecraft.server.MinecraftServer.getServer().registryAccess()
                                            .lookup(net.minecraft.core.registries.Registries.BIOME)
                                            .ifPresent(registry -> registry.keySet()
                                                    .forEach(key -> builder.suggest(key.toString())));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("key", ArgumentTypes.namespacedKey())
                                        .executes(ctx -> createBiome(ctx, plugin, true)))))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", ArgumentTypes.namespacedKey())
                                .suggests((ctx, builder) -> {
                                    if (plugin instanceof NotBiomeManager manager) {
                                        manager.getLoadedBiomeKeys().forEach(key -> builder.suggest(key.toString()));
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .suggests((ctx, builder) -> {
                                            for (String field : ALL_FIELDS) {
                                                builder.suggest("Special_Effects." + field);
                                            }
                                            List.of(
                                                    "attributes.minecraft:visual/sky_color",
                                                    "attributes.minecraft:visual/fog_color").forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .suggests((ctx, builder) -> {
                                                    String path = StringArgumentType.getString(ctx, "path");
                                                    if (path.endsWith("Particle.Type") || path.contains("particle")) {
                                                        BuiltInRegistries.PARTICLE_TYPE.keySet()
                                                                .forEach(k -> builder.suggest(k.toString()));
                                                    } else if (path.endsWith("Sound") || path.contains("sound")
                                                            || path.contains("music")) {
                                                        BuiltInRegistries.SOUND_EVENT.keySet()
                                                                .forEach(k -> builder.suggest(k.toString()));
                                                    } else if (path.endsWith("Color") || path.contains("color")) {
                                                        builder.suggest("#FFFFFF");
                                                        builder.suggest("255-255-255");
                                                    } else if (path.endsWith("Grass_Modifier")) {
                                                        builder.suggest("NONE");
                                                        builder.suggest("DARK_FOREST");
                                                        builder.suggest("SWAMP");
                                                    } else if (path.contains("Delay") || path.contains("Chance")
                                                            || path.contains("Density") || path.contains("Offset")) {
                                                        builder.suggest("1.0");
                                                        builder.suggest("0.5");
                                                        builder.suggest("100");
                                                    } else if (path.contains("Override")) {
                                                        builder.suggest("true");
                                                        builder.suggest("false");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> setBiomeProperty(ctx, plugin))))))
                .build();

        commands.register(mainCommand, "Main command for NotBiomeManager");
        commands.register(Commands.literal("nbm").redirect(mainCommand).build(), "Alias for notbiomemanager");
    }

    private static int createBiome(CommandContext<CommandSourceStack> ctx, JavaPlugin plugin, boolean hasBase) {
        NamespacedKey namespacedKey = ctx.getArgument("key", NamespacedKey.class);

        String base = "minecraft:plains";
        if (hasBase) {
            NamespacedKey baseKey = ctx.getArgument("base", NamespacedKey.class);
            base = baseKey.toString();
        }

        Path biomesFolder = Paths.get(plugin.getDataFolder().getPath(), "biomes");
        File file = biomesFolder.resolve(namespacedKey.getKey() + ".yml").toFile();

        if (file.exists()) {
            ctx.getSource().getSender().sendMessage("Biome file already exists: " + file.getName());
            return 0;
        }

        try {
            var loader = YamlConfigurationLoader.builder().file(file).build();
            CommentedConfigurationNode node = loader.createNode();
            node.node("Key").set(namespacedKey.toString());
            node.node("Base").set(base);
            loader.save(node);
            ctx.getSource().getSender()
                    .sendMessage("Created biome file " + file.getName() + ". Restart server to register.");
        } catch (Exception e) {
            e.printStackTrace();
            ctx.getSource().getSender().sendMessage("Failed to create biome file: " + e.getMessage());
            return 0;
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int setBiomeProperty(CommandContext<CommandSourceStack> ctx, JavaPlugin plugin) {
        NamespacedKey namespacedKey = ctx.getArgument("key", NamespacedKey.class);

        String path = StringArgumentType.getString(ctx, "path");
        String value = StringArgumentType.getString(ctx, "value");

        if (plugin instanceof NotBiomeManager manager) {
            Path filePath = manager.getBiomeFile(namespacedKey);

            if (filePath == null) {
                Path biomesFolder = Paths.get(plugin.getDataFolder().getPath(), "biomes");
                File potentialFile = biomesFolder.resolve(namespacedKey.getKey() + ".yml").toFile();
                if (potentialFile.exists()) {
                    filePath = potentialFile.toPath();
                } else {
                    ctx.getSource().getSender()
                            .sendMessage("Biome not found / File not found for key: " + namespacedKey);
                    return 0;
                }
            }

            try {
                var loader = YamlConfigurationLoader.builder().path(filePath).build();
                CommentedConfigurationNode node = loader.load();

                String[] pathParts = path.split("\\.");
                CommentedConfigurationNode targetNode = node;
                for (String part : pathParts) {
                    targetNode = targetNode.node(part);
                }

                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                    targetNode.set(Boolean.parseBoolean(value));
                } else {
                    try {
                        if (value.contains(".")) {
                            targetNode.set(Double.parseDouble(value));
                        } else {
                            targetNode.set(Integer.parseInt(value));
                        }
                    } catch (NumberFormatException e) {
                        targetNode.set(value);
                    }
                }

                loader.save(node);
                ctx.getSource().getSender()
                        .sendMessage("Updated " + path + " to " + value + " in " + filePath.getFileName());

                manager.reloadBiome(namespacedKey);

            } catch (Exception e) {
                e.printStackTrace();
                ctx.getSource().getSender().sendMessage("Failed to edit biome: " + e.getMessage());
                return 0;
            }

        }

        return Command.SINGLE_SUCCESS;
    }
}
