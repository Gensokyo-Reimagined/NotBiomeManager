package net.gensokyoreimagined.notbiomemanager;

import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.attribute.AttributeType;
import net.minecraft.world.attribute.AttributeTypes;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.biome.*;
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
import java.util.*;
import java.util.logging.Logger;

public class NotBiomeManager extends JavaPlugin {

    public static Logger logger;

    private CommentedConfigurationNode configNode;
    private ConfigurationLoader<CommentedConfigurationNode> loader;

    private static Field biomeSpecialEffectsField;
    private static Field biomeBuilderSpecialEffectsField;
    private static Field biomeAttributesField;
    private static Field biomeBuilderAttributesField;

    static {
        try{
            biomeSpecialEffectsField = Biome.class.getDeclaredField("specialEffects");
            biomeSpecialEffectsField.setAccessible(true);
            biomeBuilderSpecialEffectsField = Biome.BiomeBuilder.class.getDeclaredField("specialEffects");
            biomeBuilderSpecialEffectsField.setAccessible(true);
            biomeAttributesField = Biome.class.getDeclaredField("attributes");
            biomeAttributesField.setAccessible(true);
            biomeBuilderAttributesField = Biome.BiomeBuilder.class.getDeclaredField("attributes");
            biomeBuilderAttributesField.setAccessible(true);
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

    Map<Identifier,Path> biomeConfigurationFiles = new HashMap<>();

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
            CommentedConfigurationNode node = null;
            try{
                var loader = YamlConfigurationLoader.builder()
                        .file(file)
                        .defaultOptions(configurationOptions)
                        .build();
                node = loader.load();
            }catch(Exception e){
                e.printStackTrace();
                logger.severe("Failed to load biome file "+file.getName()+", stopping server.");
                getServer().shutdown();
            }
            if(node==null){
                return;
            }

            try{
                toAddBiomes.add(loadBiomeConfig(node));
                biomeConfigurationFiles.put(Identifier.bySeparator(toAddBiomes.getLast().first().asString(),':'), file.toPath());
            }catch(Exception e){
                e.printStackTrace();
                logger.severe("Failed to parse biome file "+file.getName()+" ATTEMPTING TO REGISTER PLACEHOLDER");
                try{
                    NamespacedKey key = NamespacedKey.fromString(node.node("Key").getString().toLowerCase());
                    Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME).orElseThrow();
                    Biome base = (biomes.get(Identifier.fromNamespaceAndPath("minecraft", "plains")).get()).value();
                    toAddBiomes.add(Pair.of(key, base));
                }catch(Exception e2){
                    logger.severe("Failed to register placeholder for "+file.getName()+", stopping server.");
                    getServer().shutdown();
                }
            }
        });

        for(var pair : toAddBiomes){
            logger.info("Registering biome "+pair.key());
            try{
                registerBiome(pair.key(),pair.value());
            }catch(Exception e){
                e.printStackTrace();
                logger.severe("Failed to register biome "+pair.key()+", stopping server.");
                getServer().shutdown();
            }
        }
    }

    //todo update to new system later
    private static final List<String> allFieldsOld = new ArrayList<>(List.of("Fog_Color","Water_Color","Cloud_Color","Water_Fog_Color","Sky_Color","Grass_Modifier","Foliage_Color","Dry_Foliage_Color","Grass_Color",
        "Particle.Type","Particle.Density",
        "Cave_Sound.Sound","Cave_Sound.Tick_Delay","Cave_Sound.Search_Distance","Cave_Sound.Sound_Offset",
        "Random_Sound.Sound","Random_Sound.Tick_Chance",
        "Music.Sound","Music.Min_Delay","Music.Max_Delay","Music.Override_Previous_Music"
    ));
    static {
        List<AttributeType<?>> supportedStringAttributes = List.of(
            AttributeTypes.BOOLEAN,
            AttributeTypes.TRI_STATE,
            AttributeTypes.FLOAT,
            AttributeTypes.RGB_COLOR,
            AttributeTypes.ARGB_COLOR,
            AttributeTypes.MOON_PHASE,
            AttributeTypes.BED_RULE
//            AttributeTypes.AMBIENT_PARTICLES,
//            AttributeTypes.PARTICLE,
//            AttributeTypes.BACKGROUND_MUSIC,
//            AttributeTypes.AMBIENT_SOUNDS
        );
        BuiltInRegistries.ENVIRONMENT_ATTRIBUTE.entrySet().forEach(entry -> {
            if(!supportedStringAttributes.contains(entry.getValue().type())){
                return;
            }
            allFieldsOld.add(entry.getKey().identifier().getPath().replace("/","."));
        });
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
                                    if(biomes.containsKey(Identifier.bySeparator(newBiomeId,':'))){
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
                                    biomeConfigurationFiles.put(Identifier.bySeparator(newBiomeId,':'),saveFile.toPath());
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
                                        for(String field: allFieldsOld){
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
                                    String value = StringArgumentType.getString(ctx,"value").replaceAll("\\+",":");

                                    AttributeType<?> type = null;
                                    for(var entry : BuiltInRegistries.ENVIRONMENT_ATTRIBUTE.entrySet()){
                                        if(entry.getKey().identifier().getPath().replace("/",".").equals(configKey)){
                                            type = entry.getValue().type();
                                        }
                                    }

                                    Integer valueInt = Ints.tryParse(value);
                                    Float valueFloat = Floats.tryParse(value);

                                    Identifier location = Identifier.bySeparator(biomeId,'+');

                                    Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME).orElseThrow();
                                    if(!biomes.containsKey(location)){
                                        throw new SimpleCommandExceptionType(() -> "Biome "+biomeId+" doesn't exist").create();
                                    }
                                    var biome = biomes.getValue(location);
                                    assert biome!=null;

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
                                        if(AttributeTypes.FLOAT.equals(type)||type==null){
                                            if(valueInt!=null){
                                                node.node("Special_Effects").node((Object[])configKey.split("\\.")).set(valueInt);
                                            }else if(valueFloat!=null){
                                                node.node("Special_Effects").node((Object[])configKey.split("\\.")).set(valueFloat);
                                            }else{
                                                node.node("Special_Effects").node((Object[])configKey.split("\\.")).set(value);
                                            }
                                        }else{
                                            node.node("Special_Effects").node((Object[])configKey.split("\\.")).set(value);
                                        }
                                    }catch(SerializationException e){
                                        throw new RuntimeException(e);
                                    }

                                    Biome.BiomeBuilder tempBuilder = builderFromBiome(biome);
                                    SerializationUtils.applyConfigTo(node.node("Special_Effects"),tempBuilder);

                                    try{
                                        biomeSpecialEffectsField.set(biome,biomeBuilderSpecialEffectsField.get(tempBuilder));
                                        biomeAttributesField.set(biome,((EnvironmentAttributeMap.Builder)biomeBuilderAttributesField.get(tempBuilder)).build());

                                    }catch(IllegalAccessException e){
                                        throw new RuntimeException(e);
                                    }

                                    if(loader!=null){
                                        try{
                                            loader.save(node);
                                        }catch(ConfigurateException e){
                                            ctx.getSource().getSender().sendMessage("Error while saving config value, check logs!");
                                            throw new RuntimeException(e);
                                        }
                                    }else{
                                        ctx.getSource().getSender().sendMessage("Biome "+biomeId+" doesn't have a configuration file, configuration was not saved!");
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


    public Pair<NamespacedKey,Biome> loadBiomeConfig(ConfigurationNode node){
        NamespacedKey key = NamespacedKey.fromString(node.node("Key").getString().toLowerCase());
        boolean custom = node.node("Custom").getBoolean();

        Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME).orElseThrow();
        Biome base = (biomes.get(Identifier.parse(node.node("Base").getString().toLowerCase())).get()).value();
        Biome.BiomeBuilder biomeBuilder = builderFromBiome(base);

        SerializationUtils.applyConfigTo(node.node("Special_Effects"),biomeBuilder);

        Biome biome = biomeBuilder.build();
        return Pair.of(key,biome);
    }
    public static Biome.BiomeBuilder builderFromBiome(Biome biome){
        var biomeBuilder = new Biome.BiomeBuilder();

        biomeBuilder.hasPrecipitation(biome.climateSettings.hasPrecipitation());
        biomeBuilder.temperature(biome.climateSettings.temperature());
        biomeBuilder.temperatureAdjustment(biome.climateSettings.temperatureModifier());
        biomeBuilder.downfall(biome.climateSettings.downfall());

        biomeBuilder.mobSpawnSettings(biome.getMobSettings());
        biomeBuilder.generationSettings(biome.getGenerationSettings());
        biomeBuilder.putAttributes(biome.getAttributes());
        biomeBuilder.specialEffects(biome.getSpecialEffects());
        biomeBuilder.generationSettings(BiomeGenerationSettings.EMPTY);
        return biomeBuilder;

    }

    public void loadConfig(ConfigurationNode node){

    }
    void registerBiome(NamespacedKey key,Biome biome){
        Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME).orElseThrow();
        ResourceKey<Biome> resource = ResourceKey.create(biomes.key(), Identifier.fromNamespaceAndPath(key.getNamespace(), key.getKey()));

        if (!(biomes instanceof WritableRegistry<Biome> writable)){
            throw new RuntimeException("wtfrick");
        }

        try{
            Field freezeField = MappedRegistry.class.getDeclaredField("frozen");
            freezeField.setAccessible(true);
            freezeField.set(biomes,false);
//            ((WritableRegistry<Biome>)biomes).register(resource, biome, RegistrationInfo.BUILT_IN);

            Field intrusiveHoldersField = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
            intrusiveHoldersField.setAccessible(true);
            intrusiveHoldersField.set(biomes, new HashMap<>());
//
            Holder.Reference<Biome> holder = writable.createIntrusiveHolder(biome);
//
            Field tagsField = Holder.Reference.class.getDeclaredField("tags");
            tagsField.setAccessible(true);
            tagsField.set(holder, java.util.Collections.unmodifiableSet(HashSet.newHashSet(0)));
//
            writable.register(resource,biome, RegistrationInfo.BUILT_IN);
//
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
