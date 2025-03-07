package net.gensokyoreimagined.notbiomemanager;

import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
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
import org.bukkit.command.CommandSender;
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
import org.spongepowered.configurate.yaml.NodeStyle;
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

    private static Field specialEffectsField;

    static {
        try{
            specialEffectsField = Biome.class.getDeclaredField("specialEffects");
            specialEffectsField.setAccessible(true);
        }catch(NoSuchFieldException e){
            throw new RuntimeException(e);
        }
    }


    private CommentedConfigurationNode defaultConfig(ConfigurationLoader<@NotNull CommentedConfigurationNode> loader) {
        CommentedConfigurationNode defaultConfig = loader.createNode();
        try {
            defaultConfig.node("configkey").set("configvalue").comment("placeholdercomment");
        }catch (SerializationException e){
            throw new RuntimeException(e);
        }
        return defaultConfig;
    }

    YamlConfigurationLoader.Builder builder = YamlConfigurationLoader.builder().indent(4).nodeStyle(NodeStyle.BLOCK);

    Map<ResourceLocation,Path> biomeConfigurationFiles = new HashMap<>();

    Path biomesFolder = Paths.get(getDataFolder().getPath(),"biomes");

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
        biomesFolder.toFile().mkdirs();

        List<Pair<NamespacedKey, Biome>> toAddBiomes = new ArrayList<>();
        FileUtils.iterateFiles(biomesFolder.toFile(), new String[]{"yml","yaml"},true).forEachRemaining(file -> {
            //if(FilenameUtils.getExtension(file.getPath()).equalsIgnoreCase());
            logger.info("Loading biome file "+file.getName());
            var loader = YamlConfigurationLoader.builder()
                    .file(file)
                    .defaultOptions(configurationOptions)
                    .build();
            try{
                CommentedConfigurationNode node = loader.load();
                toAddBiomes.add(loadBiomeConfig(node));
                biomeConfigurationFiles.put(ResourceLocation.bySeparator(toAddBiomes.getLast().first().asString(),':'), file.toPath());
            }catch(ConfigurateException e){
                e.printStackTrace();
            }
        });

        for(var pair : toAddBiomes){
            logger.info("Registering biome "+pair.key());
            try{
                registerBiome(pair.key(),pair.value());
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onEnable(){
        var manager = this.getLifecycleManager();
        manager.registerEventHandler(
                LifecycleEvents.COMMANDS,
                (event) -> {
                    var commands = event.registrar();
                    SimpleCommandExceptionType nonPlayerException = new SimpleCommandExceptionType(() -> {
                        return "This command can only be run by a player.";
                    });
                    var mainCommand = (
                            Commands.literal("notbiomemanager")
                                    .then(
                                            Commands.literal("reload")
                                                    .executes(ctx -> {
                                                        ctx.getSource().getSender().sendMessage("Configuration successfully reloaded!");
                                                        try{
                                                            configNode=loader.load();
                                                            loadConfig(configNode);
                                                        }catch(ConfigurateException e){
                                                            throw new RuntimeException(e);
                                                        }

                                                        return Command.SINGLE_SUCCESS;
                                                    }).requires(ctx -> actuallyHasPermission(ctx.getSender(),"notbiomemanager.reload"))
                                    )
                                    .then(
                                            Commands.literal("create")
                                                    .then(Commands.argument("newBiomeId",StringArgumentType.string()).suggests(
                                                            (a,b) -> b.suggest("namespace+id").buildFuture()
                                                    )
                                                    .then(Commands.argument("baseBiome",StringArgumentType.string()).suggests(
                                                                    (commandContext,suggestionsBuilder) -> {
                                                                        Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME).orElseThrow();
                                                                        biomes.keySet().forEach(location -> {
                                                                            if(location.getNamespace().equalsIgnoreCase("minecraft")){
                                                                                suggestionsBuilder.suggest(location.getPath());
                                                                            }
                                                                        });
                                                                        return suggestionsBuilder.buildFuture();
                                                                    }
                                                            )
                                                    .executes(ctx -> {
                                                        String newBiomeId = StringArgumentType.getString(ctx,"newBiomeId").replace('+',':');
                                                        String baseBiome = StringArgumentType.getString(ctx,"baseBiome");

                                                        Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME).orElseThrow();
                                                        if(biomes.containsKey(ResourceLocation.bySeparator(newBiomeId,':'))){
                                                            throw new SimpleCommandExceptionType(() -> "Biome "+newBiomeId+" already exists").create();
                                                        }


                                                        Path saveFilePath = Path.of(NamespacedKey.fromString(newBiomeId).namespace(),NamespacedKey.fromString(newBiomeId).value()+".yaml");

                                                        File saveFile = biomesFolder.resolve(saveFilePath).toFile();
                                                        if(saveFile.exists()){
                                                            throw new SimpleCommandExceptionType(() -> "File "+saveFilePath+" already exists, biome creation will be cancelled to not overwrite.").create();
                                                        }
                                                        saveFile.getParentFile().mkdirs();

                                                        var loader = builder.file(saveFile).build();
                                                        var defaultConfigNode = loader.createNode();
                                                        try{
                                                            defaultConfigNode.node("Custom").set(true);
                                                            defaultConfigNode.node("Key").set(NamespacedKey.fromString(newBiomeId).asString());
                                                            defaultConfigNode.node("Base").set(baseBiome);
                                                        }catch(SerializationException e){
                                                            throw new RuntimeException(e);
                                                        }


                                                        try{
                                                            loader.save(defaultConfigNode);
                                                        }catch(ConfigurateException e){
                                                            throw new RuntimeException(e);
                                                        }


                                                        var biomeEntry = loadBiomeConfig(defaultConfigNode);
                                                        registerBiome(biomeEntry.key(),biomeEntry.value());
                                                        biomeConfigurationFiles.put(ResourceLocation.bySeparator(newBiomeId,':'),saveFile.toPath());
                                                        ctx.getSource().getSender().sendMessage("Created new biome with key "+newBiomeId);


                                                        return Command.SINGLE_SUCCESS;
                                                    }).requires(ctx -> actuallyHasPermission(ctx.getSender(),"notbiomemanager.reload"))
                                    )))
                                    .then(
                                            Commands.literal("edit")
                                                    .then(Commands.argument("biome",StringArgumentType.string()).suggests(
                                                            (commandContext,suggestionsBuilder) -> {
                                                                Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME).orElseThrow();
                                                                biomes.keySet().forEach(location -> {
                                                                    suggestionsBuilder.suggest(location.getNamespace()+"+"+location.getPath());
                                                                });
                                                                return suggestionsBuilder.buildFuture();
                                                            }
                                                    )
                                                    .then(Commands.argument("configKey",StringArgumentType.string()).suggests(
                                                            (commandContext,suggestionsBuilder) -> {
                                                                for(String field: allFields){
                                                                    suggestionsBuilder.suggest(field);
                                                                }
                                                                return suggestionsBuilder
                                                                        .buildFuture();
                                                            }
                                                    )
                                                    .then(Commands.argument("value",StringArgumentType.string())
                                                    .executes(ctx -> {
                                                        String biomeId = StringArgumentType.getString(ctx,"biome");
                                                        String configKey = StringArgumentType.getString(ctx,"configKey");
                                                        if(!configKey.contains(".")){
                                                            configKey = configKey+".";
                                                        }
                                                        String value = StringArgumentType.getString(ctx,"value").replaceAll("\\+",":");
                                                        Integer valueInt = Ints.tryParse(value);
                                                        Float valueFloat = Floats.tryParse(value);

                                                        ResourceLocation location = ResourceLocation.bySeparator(biomeId,'+');

                                                        Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME).orElseThrow();
                                                        if(!biomes.containsKey(location)){
                                                            throw new SimpleCommandExceptionType(() -> "Biome "+biomeId+" doesn't exist").create();
                                                        }
                                                        var biome = biomes.getValue(location);
                                                        var builder = SpecialEffectsBuilder.getSpecialEffects(biome);

                                                        CommentedConfigurationNode node;
                                                        YamlConfigurationLoader loader = null;
                                                        if(biomeConfigurationFiles.containsKey(location)){
                                                            try{
                                                                loader = this.builder.file(biomeConfigurationFiles.get(location).toFile()).build();
                                                                node = loader.load();
                                                            }catch(ConfigurateException e){
                                                                throw new RuntimeException(e);
                                                            }
                                                        }else{
                                                            node = YamlConfigurationLoader.builder().build().createNode();
                                                        }

                                                        try{
                                                            if(valueInt!=null){
                                                                node.node("Special_Effects").node((Object[])configKey.split("\\.")).set(valueInt);
                                                            }else if(valueFloat!=null){
                                                                node.node("Special_Effects").node((Object[])configKey.split("\\.")).set(valueFloat);
                                                            }else{
                                                                node.node("Special_Effects").node((Object[])configKey.split("\\.")).set(value);
                                                            }
                                                        }catch(SerializationException e){
                                                            throw new RuntimeException(e);
                                                        }

                                                        applyConfigTo(node.node("Special_Effects"),builder);

                                                        try{
                                                            specialEffectsField.set(biome,builder.build());
                                                        }catch(IllegalAccessException e){
                                                            throw new RuntimeException(e);
                                                        }

                                                        if(loader!=null){
                                                            try{
                                                                loader.save(node);
                                                            }catch(ConfigurateException e){
                                                                throw new RuntimeException(e);
                                                            }
                                                        }


                                                        ctx.getSource().getSender().sendMessage("Set value "+configKey+" to "+value+" for biome "+biomeId);

                                                        return Command.SINGLE_SUCCESS;
                                                    }))))
                                    )
                                    .build()
                    );
                    commands.register(mainCommand);
                    commands.register(Commands.literal("nbm").redirect(mainCommand).build());



                }
        );

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

        applyConfigTo(node.node("Special_Effects"),specialEffectsBuilder);


        biomeBuilder.specialEffects(specialEffectsBuilder.build());

        Biome biome = biomeBuilder.build();
        return Pair.of(key,biome);
    }

    private static final String[] allFields = new String[]{"Fog_Color","Water_Color","Water_Fog_Color","Sky_Color","Grass_Modifier","Foliage_Color","Grass_Color",
            "Particle.Type","Particle.Density",
            "Cave_Sound.Sound","Cave_Sound.Tick_Delay","Cave_Sound.Search_Distance","Cave_Sound.Sound_Offset",
            "Random_Sound.Sound","Random_Sound.Tick_Chance",
            "Music.Sound","Music.Min_Delay","Music.Max_Delay","Music.Override_Previous_Music"
    };

    void applyConfigTo(ConfigurationNode node, SpecialEffectsBuilder specialEffectsBuilder){


        compute(node.node("Fog_Color"),x -> specialEffectsBuilder.fogColor(fromRgbString(x.getString()).asRGB()));
        compute(node.node("Water_Color"),x -> specialEffectsBuilder.waterColor(fromRgbString(x.getString()).asRGB()));
        compute(node.node("Water_Fog_Color"),x -> specialEffectsBuilder.waterFogColor(fromRgbString(x.getString()).asRGB()));
        compute(node.node("Sky_Color"),x -> specialEffectsBuilder.skyColor(fromRgbString(x.getString()).asRGB()));

        compute(node.node("Grass_Modifier"),x -> specialEffectsBuilder.grassColorModifier(BiomeSpecialEffects.GrassColorModifier.valueOf(x.getString())));

        compute(node.node("Foliage_Color"),x -> specialEffectsBuilder.foliageColorOverride(fromRgbString(x.getString()).asRGB()));
        compute(node.node("Grass_Color"),x -> specialEffectsBuilder.grassColorOverride(fromRgbString(x.getString()).asRGB()));

        var ambientParticleNode = node.node("Particle");
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

        var ambientMoodNode = node.node("Cave_Sound");
        compute(ambientMoodNode.node("Sound"), x -> specialEffectsBuilder.ambientMoodSoundEvent = Optional.ofNullable(getSoundFromKey(x.getString())));
        compute(ambientMoodNode.node("Tick_Delay"), x -> specialEffectsBuilder.ambientMoodTickDelay = Optional.of(x.getInt()));
        compute(ambientMoodNode.node("Search_Distance"), x -> specialEffectsBuilder.ambientMoodBlockSearchExtent = Optional.of(x.getInt()));
        compute(ambientMoodNode.node("Sound_Offset"), x -> specialEffectsBuilder.ambientMoodSoundPositionOffset = Optional.of(x.getDouble()));


        var ambientAdditionsNode = node.node("Random_Sound");
        compute(ambientAdditionsNode.node("Sound"), x -> specialEffectsBuilder.ambientAdditionsSoundEvent = Optional.ofNullable(getSoundFromKey(x.getString())));
        compute(ambientAdditionsNode.node("Tick_Chance"), x -> specialEffectsBuilder.ambientAdditionsTickChance = Optional.of(x.getDouble()));



        compute(node.node("Music"),x -> {
            if(x.isList()){
                throw new RuntimeException("Unsupported list syntax for Music");
            }else{
                compute(x.node("Sound"), y -> specialEffectsBuilder.singleBackgroundMusicSoundEvent = Optional.ofNullable(getSoundFromKey(y.getString())));
                compute(x.node("Min_Delay"), y -> specialEffectsBuilder.singleBackgroundMusicMinDelay = Optional.of(x.getInt()));
                compute(x.node("Max_Delay"), y -> specialEffectsBuilder.singleBackgroundMusicMaxDelay = Optional.of(x.getInt()));
                compute(x.node("Override_Previous_Music"), y -> specialEffectsBuilder.singleBackgroundMusicReplaceCurrentMusic = Optional.of(y.getBoolean()));
            }
        });
    }

//    void saveBiomeTo(ConfigurationNode node, ResourceLocation location, Biome biome) throws SerializationException{
//        node.node("Key").set(location.getNamespace()+":"+location.getPath());
//        node.node("Custom").set(true);
//        node.node("Base")
//    }


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


    public static boolean actuallyHasPermission(CommandSender sender,String permission) {
        if (sender.isPermissionSet(permission)) {
            return sender.hasPermission(permission);
        }
        int position = permission.length();

        for(int i = permission.split("\\.").length-1;i>0;i--){
            String wildcardPerm = permission.substring(0,permission.lastIndexOf(".",position)) + ".*";
            position = permission.lastIndexOf(".",position);
            if (sender.isPermissionSet(wildcardPerm)) {
                return sender.hasPermission(wildcardPerm);
            }
        }
        return sender.hasPermission(permission);
    }
}
