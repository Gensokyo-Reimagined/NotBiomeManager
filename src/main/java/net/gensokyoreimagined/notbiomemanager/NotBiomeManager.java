package net.gensokyoreimagined.notbiomemanager;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.biome.*;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.*;
import java.util.function.Consumer;
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

        List<Pair<NamespacedKey, Biome>> toAddBiomes = new ArrayList<>();
        FileUtils.iterateFiles(biomesFolder.toFile(), new String[]{"yml","yaml"},true).forEachRemaining(file -> {
            //if(FilenameUtils.getExtension(file.getPath()).equalsIgnoreCase());
            System.out.println("Loading biome file "+file.getName());
            var loader = YamlConfigurationLoader.builder()
                    .file(file)
                    .defaultOptions(configurationOptions)
                    .build();
            try{
                CommentedConfigurationNode node = loader.load();
                toAddBiomes.add(loadBiomeConfig(node));
            }catch(ConfigurateException e){
                e.printStackTrace();
            }
        });

        for(var pair : toAddBiomes){
            System.out.println("Registering biome "+pair.key());
            try{
                registerBiome(pair.key(),pair.value());
            }catch(Exception e){
                e.printStackTrace();
            }
        }

    }

    public static Color fromRgbString(String string){
        var splitted = string.split("-");
        return Color.fromRGB(Integer.parseInt(splitted[0]),Integer.parseInt(splitted[1]),Integer.parseInt(splitted[2]));
    }

    Holder<SoundEvent> getSoundFromKey(String key){
        return getSoundFromKey(NamespacedKey.fromString(key.toLowerCase()));
    }

    @Nullable
    Holder<SoundEvent> getSoundFromKey(NamespacedKey key){
        return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath(key.namespace(),key.value())).orElse(null);
    }


    void compute(ConfigurationNode node,Consumer<ConfigurationNode> consumer){
        if(!node.empty()){
            consumer.accept(node);
        }
    }

    public Pair<NamespacedKey,Biome> loadBiomeConfig(ConfigurationNode node){
        NamespacedKey key = NamespacedKey.fromString(node.node("Key").getString().toLowerCase());
        boolean custom = node.node("Custom").getBoolean();

        Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME).orElseThrow();
        Biome base = (biomes.get(ResourceLocation.fromNamespaceAndPath("minecraft", node.node("Base").getString().toLowerCase())).get()).value();
        var biomeBuilder = new Biome.BiomeBuilder();

        biomeBuilder.hasPrecipitation(base.climateSettings.hasPrecipitation());
        biomeBuilder.temperature(base.climateSettings.temperature());
        biomeBuilder.temperatureAdjustment(base.climateSettings.temperatureModifier());
        biomeBuilder.downfall(base.climateSettings.downfall());

        biomeBuilder.mobSpawnSettings(base.getMobSettings());
        biomeBuilder.generationSettings(base.getGenerationSettings());

        var specialEffectsBuilder = SpecialEffectsBuilder.getSpecialEffects(base);
        var specialEffectsNode = node.node("Special_Effects");


        compute(specialEffectsNode.node("Fog_Color"), x -> specialEffectsBuilder.fogColor(fromRgbString(x.getString()).asRGB()));
        compute(specialEffectsNode.node("Water_Color"), x -> specialEffectsBuilder.waterColor(fromRgbString(x.getString()).asRGB()));
        compute(specialEffectsNode.node("Water_Fog_Color"), x -> specialEffectsBuilder.waterFogColor(fromRgbString(x.getString()).asRGB()));
        compute(specialEffectsNode.node("Sky_Color"), x -> specialEffectsBuilder.skyColor(fromRgbString(x.getString()).asRGB()));
//        specialEffectsBuilder.fogColor((fromRgbString(getOrDefault(specialEffectsNode.node("Fog_Color"),toRgbString(Color.fromRGB(base.getFogColor()))))).asRGB());
//        specialEffectsBuilder.waterColor((fromRgbString(getOrDefault(specialEffectsNode.node("Water_Color"),toRgbString(Color.fromRGB(base.getFogColor()))))).asRGB());
//        specialEffectsBuilder.waterFogColor((fromRgbString(getOrDefault(specialEffectsNode.node("Water_Fog_Color"),toRgbString(Color.fromRGB(base.getFogColor()))))).asRGB());
//        specialEffectsBuilder.skyColor((fromRgbString(getOrDefault(specialEffectsNode.node("Sky_Color"),toRgbString(Color.fromRGB(base.getFogColor()))))).asRGB());

        compute(specialEffectsNode.node("Grass_Modifier"), x -> specialEffectsBuilder.grassColorModifier(BiomeSpecialEffects.GrassColorModifier.valueOf(x.getString())));

        compute(specialEffectsNode.node("Foliage_Color"), x -> specialEffectsBuilder.foliageColorOverride(fromRgbString(x.getString()).asRGB()));

        var ambientParticleNode = specialEffectsNode.node("Particle");
        compute(ambientParticleNode.node("Type"), x -> {
            RegistryAccess access = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
            ParticleOptions nmsParticle = null;
            try{
                nmsParticle = ParticleArgument.readParticle(new StringReader(x.getString()), access);
            }catch(CommandSyntaxException e){
                throw new RuntimeException(e);
            }
            specialEffectsBuilder.ambientParticleParticleOptions = Optional.of(nmsParticle);
        });
        compute(ambientParticleNode.node("Density"), x -> specialEffectsBuilder.ambientParticleProbability = Optional.of(x.getFloat()));

        var ambientMoodNode = specialEffectsNode.node("Cave_Sound");
        compute(ambientMoodNode.node("Sound"), x -> specialEffectsBuilder.ambientMoodSoundEvent = Optional.ofNullable(getSoundFromKey(x.getString())));
        compute(ambientMoodNode.node("Tick_Delay"), x -> specialEffectsBuilder.ambientMoodTickDelay = Optional.of(x.getInt()));
        compute(ambientMoodNode.node("Search_Distance"), x -> specialEffectsBuilder.ambientMoodBlockSearchExtent = Optional.of(x.getInt()));
        compute(ambientMoodNode.node("Sound_Offset"), x -> specialEffectsBuilder.ambientMoodSoundPositionOffset = Optional.of(x.getDouble()));


        var ambientAdditionsNode = specialEffectsNode.node("Random_Sound");
        compute(ambientAdditionsNode.node("Sound"), x -> specialEffectsBuilder.ambientAdditionsSoundEvent = Optional.ofNullable(getSoundFromKey(x.getString())));
        compute(ambientAdditionsNode.node("Tick_Chance"), x -> specialEffectsBuilder.ambientAdditionsTickChance = Optional.of(x.getDouble()));



        compute(specialEffectsNode.node("Music"), x -> {
            if(x.isList()){
                throw new RuntimeException("Unsupported list syntax for Music");
            }else{
                compute(x.node("Sound"), y -> specialEffectsBuilder.singleBackgroundMusicSoundEvent = Optional.ofNullable(getSoundFromKey(y.getString())));
                compute(x.node("Min_Delay"), y -> specialEffectsBuilder.singleBackgroundMusicMinDelay = Optional.of(x.getInt()));
                compute(x.node("Max_Delay"), y -> specialEffectsBuilder.singleBackgroundMusicMaxDelay = Optional.of(x.getInt()));
                compute(x.node("Override_Previous_Music"), y -> specialEffectsBuilder.singleBackgroundMusicReplaceCurrentMusic = Optional.of(y.getBoolean()));
            }
        });

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
