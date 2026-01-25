package net.gensokyoreimagined.notbiomemanager;

import com.mojang.brigadier.StringReader;
import net.minecraft.IdentifierException;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.TriState;
import net.minecraft.world.attribute.*;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftServer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class SerializationUtils {
    public static Color fromRgbString(String string){
        var splitted = string.split("-");
        if(splitted.length == 1){
            return Color.fromRGB(Integer.parseInt(splitted[0].replaceAll("#",""),16));
        }
        return Color.fromRGB(Integer.parseInt(splitted[0]),Integer.parseInt(splitted[1]),Integer.parseInt(splitted[2]));
    }

    public static Color fromArgbString(String string){
        var splitted = string.split("-");
        if(splitted.length == 1){
            return Color.fromARGB((int)Long.parseLong(splitted[0].replaceAll("#","").replaceAll(" ",""),16));
        }
        return Color.fromARGB(Integer.parseInt(splitted[0]),Integer.parseInt(splitted[1]),Integer.parseInt(splitted[2]),Integer.parseInt(splitted[3]));
    }

    public static Holder<SoundEvent> getSoundFromKey(String key){
        return getSoundFromKey(NamespacedKey.fromString(key.toLowerCase()));
    }

    @Nullable
    public static Music getMusic(ConfigurationNode node){
        if(node.empty()){
            return null;
        }
        return new Music(
                getSoundFromKey(NamespacedKey.fromString(node.node("sound").getString().toLowerCase())),
                node.node("minDelay").getInt(),
                node.node("maxDelay").getInt(),
                node.node("replaceCurrentMusic").getBoolean()
        );
    }

    public static Holder<SoundEvent> getSoundFromKey(NamespacedKey key){
        Optional<Holder.Reference<SoundEvent>> sound = BuiltInRegistries.SOUND_EVENT.get(Identifier.fromNamespaceAndPath(key.namespace(),key.value()));
        if(sound.isPresent()){
            return sound.get();
        }else{
            throw new RuntimeException("Sound "+Identifier.fromNamespaceAndPath(key.namespace(),key.value())+" doesn't exist!");
        }
    }
    public static String getStringPath(EnvironmentAttribute<?> attr){
        return BuiltInRegistries.ENVIRONMENT_ATTRIBUTE.getKey(attr).getPath().replace('/','.');
    }
    public static AmbientMoodSettings getMoodSettings(ConfigurationNode node){
        return new AmbientMoodSettings(
                getSoundFromKey(node.node("sound").getString()),
                node.node("tickDelay").getInt(),
                node.node("blockSearchExtent").getInt(),
                node.node("soundPositionOffset").getDouble()
        );
    }
    public static AmbientAdditionsSettings getAdditionsSettings(ConfigurationNode node){
        return new AmbientAdditionsSettings(
                getSoundFromKey(node.node("sound").getString()),
                node.node("tickChance").getDouble()
        );
    }

    public static void compute(ConfigurationNode node,Consumer<ConfigurationNode> consumer){
        if(!node.empty()){
            consumer.accept(node);
        }
    }

    public static void visit(ConfigurationNode node,List<String> path,BiPredicate<List<String>,ConfigurationNode> consumer){
        if(!consumer.test(path,node)){
            return;
        }
        node.childrenMap().forEach((key,child) -> {
            if(!(key instanceof String)){
                return;
            }
            path.add((String)key);
            visit(child,path,consumer);
            path.removeLast();
        });
    }

    private static final Map<String,String> allFieldsOldMap = Map.ofEntries(
        Map.entry("Fog_Color", SerializationUtils.getStringPath(EnvironmentAttributes.FOG_COLOR)),
        Map.entry("Cloud_Color", SerializationUtils.getStringPath(EnvironmentAttributes.CLOUD_COLOR)),
        Map.entry("Water_Fog_Color", SerializationUtils.getStringPath(EnvironmentAttributes.WATER_FOG_COLOR)),
        Map.entry("Sky_Color", SerializationUtils.getStringPath(EnvironmentAttributes.SKY_COLOR)),
        Map.entry("Fog_Start_Distance", SerializationUtils.getStringPath(EnvironmentAttributes.FOG_START_DISTANCE)),
        Map.entry("Fog_End_Distance", SerializationUtils.getStringPath(EnvironmentAttributes.FOG_END_DISTANCE)),
        Map.entry("Water_Fog_Start_Distance", SerializationUtils.getStringPath(EnvironmentAttributes.WATER_FOG_START_DISTANCE)),
        Map.entry("Water_Fog_End_Distance", SerializationUtils.getStringPath(EnvironmentAttributes.WATER_FOG_END_DISTANCE)),
        Map.entry("Particle.Type", SerializationUtils.getStringPath(EnvironmentAttributes.AMBIENT_PARTICLES)+"[].particle"),
        Map.entry("Particle.Density", SerializationUtils.getStringPath(EnvironmentAttributes.AMBIENT_PARTICLES)+"[].probability"),
        Map.entry("Cave_Sound.Sound", SerializationUtils.getStringPath(EnvironmentAttributes.AMBIENT_SOUNDS)+".moodSettings.sound"),
        Map.entry("Cave_Sound.Tick_Delay", SerializationUtils.getStringPath(EnvironmentAttributes.AMBIENT_SOUNDS)+".moodSettings.tickDelay"),
        Map.entry("Cave_Sound.Search_Distance", SerializationUtils.getStringPath(EnvironmentAttributes.AMBIENT_SOUNDS)+".moodSettings.searchExtent"),
        Map.entry("Cave_Sound.Sound_Offset", SerializationUtils.getStringPath(EnvironmentAttributes.AMBIENT_SOUNDS)+".moodSettings.soundPositionOffset"),
        Map.entry("Random_Sound.Sound", SerializationUtils.getStringPath(EnvironmentAttributes.AMBIENT_SOUNDS)+".additions[].sound"),
        Map.entry("Random_Sound.Tick_Chance", SerializationUtils.getStringPath(EnvironmentAttributes.AMBIENT_SOUNDS)+".additions[].tickChance"),
        Map.entry("Music.Sound", SerializationUtils.getStringPath(EnvironmentAttributes.BACKGROUND_MUSIC)+".defaultMusic.sound"),
        Map.entry("Music.Min_Delay", SerializationUtils.getStringPath(EnvironmentAttributes.BACKGROUND_MUSIC)+".defaultMusic.minDelay"),
        Map.entry("Music.Max_Delay", SerializationUtils.getStringPath(EnvironmentAttributes.BACKGROUND_MUSIC)+".defaultMusic.maxDelay"),
        Map.entry("Music.Override_Previous_Music", SerializationUtils.getStringPath(EnvironmentAttributes.BACKGROUND_MUSIC)+".defaultMusic.replaceCurrentMusic")

        //still not in environmentattributes
//        "Water_Color",
//        "Grass_Modifier",
//        "Foliage_Color",
//        "Dry_Foliage_Color",
//        "Grass_Color",
    );
    private static final List<String> nonAttributeFields = List.of(
        "Water_Color",
        "Grass_Modifier",
        "Foliage_Color",
        "Dry_Foliage_Color",
        "Grass_Color"
    );

    private static Field biomeBuilderSpecialEffectsField;

    static {
        try{
            biomeBuilderSpecialEffectsField = Biome.BiomeBuilder.class.getDeclaredField("specialEffects");
            biomeBuilderSpecialEffectsField.setAccessible(true);
        }catch(NoSuchFieldException e){
            throw new RuntimeException(e);
        }
    }
    public static void applyConfigTo(ConfigurationNode baseNode, Biome.BiomeBuilder biomeBuilder) {
        baseNode = baseNode.copy();
//        try{
//            System.out.println("the node was originally "+HoconConfigurationLoader.builder().buildAndSaveString(baseNode));
//        }catch(ConfigurateException e){
//            throw new RuntimeException(e);
//        }

        //migrate old fields
        for(Map.Entry<String,String> entry: allFieldsOldMap.entrySet()){
            ConfigurationNode origNode = baseNode.node((Object[])entry.getKey().split("\\."));
            if(origNode.empty()){
                continue;
            }
            ConfigurationNode origNodeCopy = origNode.copy();
            ConfigurationNode toRemoveFrom = origNode.parent();
            toRemoveFrom.removeChild(origNode.key());
            while(toRemoveFrom.empty()){
                ConfigurationNode temp = toRemoveFrom.parent();
                temp.removeChild(toRemoveFrom.key());
                toRemoveFrom = temp;
            }

            String[] newPath = entry.getValue().split("\\.");
            ConfigurationNode newNode = baseNode;
            for(String key : newPath){
                if(key.contains("[]")){
                    key = key.replace("[]","");
                    newNode = newNode.node(key);
                    if(newNode.isList()){
                        newNode = newNode.childrenList().getFirst();
                    }else{
                        newNode = newNode.appendListNode();
                    }
                }else{
                    newNode = newNode.node(key);
                }
            }
//            System.out.println(entry.getKey()+" -> "+entry.getValue());
            newNode.mergeFrom(origNodeCopy);
        }
//        try{
//            System.out.println("the node is now "+HoconConfigurationLoader.builder().buildAndSaveString(baseNode));
//        }catch(ConfigurateException e){
//            throw new RuntimeException(e);
//        }


        SerializationUtils.visit(baseNode,new ArrayList<>(),(path,child) -> {
            if(nonAttributeFields.contains(String.join(".",path))){
                return false;
            }
//            System.out.println("VISITED PATH "+String.join(".",path));
            Optional<Holder.Reference<EnvironmentAttribute<?>>> optional = Optional.empty();
            try{
                optional = BuiltInRegistries.ENVIRONMENT_ATTRIBUTE.get(Identifier.withDefaultNamespace(String.join("/",path)));
            }catch(IdentifierException e){
                NotBiomeManager.logger.warning("Field "+String.join(".",path)+" was not a valid identifier!");
            }
            if(optional.isEmpty()){
                return true;
            }
            EnvironmentAttribute attribute = optional.get().value();
            try{
                if(attribute.type()==AttributeTypes.BOOLEAN){
                    biomeBuilder.setAttribute(attribute,child.getBoolean());
                }else if(attribute.type()==AttributeTypes.TRI_STATE){
                    biomeBuilder.setAttribute(attribute,TriState.valueOf(child.getString().toUpperCase()));
                }else if(attribute.type()==AttributeTypes.FLOAT){
                    biomeBuilder.setAttribute(attribute,child.getFloat());
//                }else if(attribute.type()==AttributeTypes.ANGLE_DEGREES){ //not needed atm
                }else if(attribute.type()==AttributeTypes.RGB_COLOR){
                    biomeBuilder.setAttribute(attribute,SerializationUtils.fromRgbString(child.getString()).asRGB());
                }else if(attribute.type()==AttributeTypes.ARGB_COLOR){
                    biomeBuilder.setAttribute(attribute,SerializationUtils.fromArgbString(child.getString()).asARGB());
                }else if(attribute.type()==AttributeTypes.MOON_PHASE){
                    biomeBuilder.setAttribute(attribute,MoonPhase.valueOf(child.getString().toUpperCase()));
//                }else if(attribute.type()==AttributeTypes.ACTIVITY){ //not needed
                }else if(attribute.type()==AttributeTypes.BED_RULE){
                    if(child.getString().equalsIgnoreCase("CAN_SLEEP_WHEN_DARK")){
                        biomeBuilder.setAttribute(attribute,BedRule.CAN_SLEEP_WHEN_DARK);
                    }else if(child.getString().equalsIgnoreCase("EXPLODES")){
                        biomeBuilder.setAttribute(attribute,BedRule.EXPLODES);
                    }else{
                        throw new RuntimeException("Value "+child.getString()+" is not one of \"CAN_SLEEP_WHEN_DARK\" or \"EXPLODES\"");
                    }
                }else if(attribute.type()==AttributeTypes.AMBIENT_PARTICLES){
                    ArrayList<AmbientParticle> particles = new ArrayList<>();
                    for(ConfigurationNode particleNode : child.childrenList()){
                        particles.add(
                            new AmbientParticle(
                                ParticleArgument.readParticle(
                                    new StringReader(particleNode.node("particle").getString()),
                                    ((CraftServer)Bukkit.getServer()).getServer().registryAccess()
                                ),
                                particleNode.node("probability").getFloat()
                            )
                        );
                    }
                    biomeBuilder.setAttribute(attribute,particles);
                }else if(attribute.type()==AttributeTypes.PARTICLE){
                    biomeBuilder.setAttribute(attribute,ParticleArgument.readParticle(
                        new StringReader(child.getString()),
                        ((CraftServer)Bukkit.getServer()).getServer().registryAccess()
                    ));
                }else if(attribute.type()==AttributeTypes.BACKGROUND_MUSIC){
                    biomeBuilder.setAttribute(attribute, new BackgroundMusic(
                        Optional.ofNullable(SerializationUtils.getMusic(child.node("defaultMusic"))),
                        Optional.ofNullable(SerializationUtils.getMusic(child.node("creativeMusic"))),
                        Optional.ofNullable(SerializationUtils.getMusic(child.node("underwaterMusic")))
                    ));
                }else if(attribute.type()==AttributeTypes.AMBIENT_SOUNDS){
                    ArrayList<AmbientAdditionsSettings> ambientAdditions = new ArrayList<>();
                    for(ConfigurationNode additionNode : child.node("additions").childrenList()){
                        ambientAdditions.add(SerializationUtils.getAdditionsSettings(additionNode));
                    }
                    Optional<AmbientMoodSettings> moodSettings = Optional.empty();
                    if(!child.node("moodSettings").empty()){
                        moodSettings = Optional.of(SerializationUtils.getMoodSettings(child.node("moodSettings")));
                    }

                    biomeBuilder.setAttribute(attribute, new AmbientSounds(
                        Optional.ofNullable(child.node("loop").getString()).map(SerializationUtils::getSoundFromKey),
                        moodSettings,
                        ambientAdditions
                    ));
                }else{
                    throw new RuntimeException("Attribute type "+attribute.type()+" for config value "+String.join(".",path)+" is not supported");
                }
                return false;
            }catch(Exception e){
                NotBiomeManager.logger.severe("Error "+e.getMessage()+" while applying config value "+String.join(".",path)+" to biome, skipping value");
                e.printStackTrace();
                return false;
            }
        });

        BiomeSpecialEffects origSpecialEffects = null;
        try{
            origSpecialEffects = (BiomeSpecialEffects)biomeBuilderSpecialEffectsField.get(biomeBuilder);
        }catch(IllegalAccessException e){
            throw new RuntimeException(e);
        }
        if(origSpecialEffects==null){
            origSpecialEffects = new BiomeSpecialEffects.Builder().build();
        }
        BiomeSpecialEffects.Builder biomeEffectsBuilder = new BiomeSpecialEffects.Builder()
            .waterColor(origSpecialEffects.waterColor());
        origSpecialEffects.foliageColorOverride().ifPresent(biomeEffectsBuilder::foliageColorOverride);
        origSpecialEffects.dryFoliageColorOverride().ifPresent(biomeEffectsBuilder::dryFoliageColorOverride);
        origSpecialEffects.grassColorOverride().ifPresent(biomeEffectsBuilder::grassColorOverride);
        biomeEffectsBuilder.grassColorModifier(origSpecialEffects.grassColorModifier());

        SerializationUtils.compute(baseNode.node("Water_Color"),x -> biomeEffectsBuilder.waterColor(SerializationUtils.fromRgbString(x.getString()).asRGB()));
        SerializationUtils.compute(baseNode.node("Grass_Modifier"),x -> biomeEffectsBuilder.grassColorModifier(BiomeSpecialEffects.GrassColorModifier.valueOf(x.getString())));
        SerializationUtils.compute(baseNode.node("Foliage_Color"),x -> biomeEffectsBuilder.foliageColorOverride(SerializationUtils.fromRgbString(x.getString()).asRGB()));
        SerializationUtils.compute(baseNode.node("Dry_Foliage_Color"),x -> biomeEffectsBuilder.dryFoliageColorOverride(SerializationUtils.fromRgbString(x.getString()).asRGB()));
        SerializationUtils.compute(baseNode.node("Grass_Color"),x -> biomeEffectsBuilder.grassColorOverride(SerializationUtils.fromRgbString(x.getString()).asRGB()));
        biomeBuilder.specialEffects(biomeEffectsBuilder.build());
    }

//    void saveBiomeTo(ConfigurationNode node, ResourceLocation location, Biome biome) throws SerializationException{
//        node.node("Key").set(location.getNamespace()+":"+location.getPath());
//        node.node("Custom").set(true);
//        node.node("Base")
//    }

}
