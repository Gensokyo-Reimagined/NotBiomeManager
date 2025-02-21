package net.gensokyoreimagined.notbiomemanager;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

public class NotBiomeManager extends JavaPlugin {

    public static Logger logger;

    private CommentedConfigurationNode configNode;
    private ConfigurationLoader<CommentedConfigurationNode> loader;



    private CommentedConfigurationNode defaultConfig(ConfigurationLoader<@NotNull CommentedConfigurationNode> loader) {
        CommentedConfigurationNode defaultConfig = loader.createNode();
        try {
            defaultConfig.node("configkey").set("configvalue").comment("placeholdercomment");
        }catch (SerializationException e){
            throw new RuntimeException(e);
        }
        return defaultConfig;
    }

    @Override
    public void onLoad(){
        logger=getLogger();

        File configFile = new File(getDataFolder(),"config.conf");
        ConfigurationOptions configurationOptions = ConfigurationOptions.defaults();
        loader = HoconConfigurationLoader.builder()
                .file(configFile)
                .defaultOptions(configurationOptions)
                .build();

        try {
            if(!configFile.exists()){
                loader.save(defaultConfig(loader));
            }
            configNode = loader.load();
            configNode.mergeFrom(defaultConfig(loader));
            loader.save(configNode);
        } catch (ConfigurateException e) {
            throw new RuntimeException(e);
        }
        Path biomesFolder = Paths.get(getDataFolder().getPath(),"biomes");
        biomesFolder.toFile().mkdirs();

        for(File file: biomesFolder.toFile().listFiles()){
            //if(FilenameUtils.getExtension(file.getPath()).equalsIgnoreCase());
            var loader = YamlConfigurationLoader.builder()
                    .file(file)
                    .defaultOptions(configurationOptions)
                    .build();
            try{
                CommentedConfigurationNode node = loader.load();

            }catch(ConfigurateException e){
                e.printStackTrace();
                continue;
            }

        }


        NamespacedKey namespacedKey = new NamespacedKey("minecraft","plains");


        Biome biome = new Biome.BiomeBuilder()
                .temperature(0.0f)
                .downfall(0.0f)
                .specialEffects(
                        new BiomeSpecialEffects.Builder()
                                .fogColor(Color.fromARGB(0,0,0,255).asRGB())
                                .waterColor(Color.fromARGB(0,0,255,0).asRGB())
                                .waterFogColor(Color.fromARGB(0,0,255,255).asRGB())
                                .skyColor(Color.fromARGB(0,255,0,255).asRGB())
                                .build()
                )
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
        registerBiome(namespacedKey,biome);




    }

    public static Color fromRgbString(String string){
        var splitted = string.split("-");
        return Color.fromRGB(Integer.parseInt(splitted[0]),Integer.parseInt(splitted[1]),Integer.parseInt(splitted[2]));
    }

    public static String toRgbString(Color color){
        return color.getRed()+"-"+color.getGreen()+"-"+color.getBlue();
    }

    String getOrDefault(ConfigurationNode node, String defaultValue){
        var value = node.getString();
        if(value == null){
            value = defaultValue;
        }
        return value;
    }

    public Pair<NamespacedKey,Biome> loadBiomeConfig(ConfigurationNode node){
        NamespacedKey key = NamespacedKey.fromString(node.node("Key").getString());
        boolean custom = node.node("Custom").getBoolean();

        Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME).orElseThrow();
        Biome base = (biomes.get(ResourceLocation.fromNamespaceAndPath("minecraft", node.node("Base").getString())).get()).value();
        var biomeBuilder = new Biome.BiomeBuilder();
        var specialEffectsBuilder = new BiomeSpecialEffects.Builder();
        var specialEffectsNode = node.node("Special_Effects");

        specialEffectsBuilder.fogColor((fromRgbString(getOrDefault(specialEffectsNode.node("Fog_Color"),toRgbString(Color.fromRGB(base.getFogColor()))))).asRGB());
        specialEffectsBuilder.waterColor((fromRgbString(getOrDefault(specialEffectsNode.node("Water_Color"),toRgbString(Color.fromRGB(base.getFogColor()))))).asRGB());
        specialEffectsBuilder.waterFogColor((fromRgbString(getOrDefault(specialEffectsNode.node("Water_Fog_Color"),toRgbString(Color.fromRGB(base.getFogColor()))))).asRGB());
        specialEffectsBuilder.skyColor((fromRgbString(getOrDefault(specialEffectsNode.node("Sky_Color"),toRgbString(Color.fromRGB(base.getFogColor()))))).asRGB());

        if(specialEffectsNode.node("Grass_Modified").getString() !=null){
            specialEffectsBuilder.grassColorModifier(BiomeSpecialEffects.GrassColorModifier.valueOf(specialEffectsNode.node("Grass_Modified").getString()));
        }

        biomeBuilder.specialEffects(specialEffectsBuilder.build());

        return Pair.of(key,biomeBuilder.build());
    }


    public void loadConfig(ConfigurationNode node){

    }
    void registerBiome(NamespacedKey key,Biome biome){
        Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME).orElseThrow();
        ResourceKey<Biome> resource = ResourceKey.create(biomes.key(), ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getKey()));

        if (!(biomes instanceof WritableRegistry<Biome> writable)){
            throw new RuntimeException("wtfrick");
        }

        try{
            Field freezeField = MappedRegistry.class.getDeclaredField("frozen");
            freezeField.setAccessible(true);
            freezeField.set(biomes,false);

            Field intrusiveHoldersField = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
            intrusiveHoldersField.setAccessible(true);
            intrusiveHoldersField.set(biomes, new HashMap<>());

            Holder.Reference<Biome> holder = writable.createIntrusiveHolder(biome);

            Field tagsField = Holder.Reference.class.getDeclaredField("tags");
            tagsField.setAccessible(true);
            tagsField.set(holder, java.util.Collections.unmodifiableSet(HashSet.newHashSet(0)));

            writable.register(resource,biome, RegistrationInfo.BUILT_IN);

            intrusiveHoldersField.set(biomes,null);
            freezeField.set(biomes,true);

        }catch(NoSuchFieldException|IllegalAccessException e){
            throw new RuntimeException(e);
        }
    }
}
