package net.gensokyoreimagined.notbiomemanager;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.biome.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Optional;

public class SpecialEffectsBuilder extends BiomeSpecialEffects.Builder {
    private SpecialEffectsBuilder(){}
    public static SpecialEffectsBuilder getSpecialEffects(Biome biome) {
        BiomeSpecialEffects effects = biome.getSpecialEffects();

        SpecialEffectsBuilder builder = new SpecialEffectsBuilder();
        builder.fogColor(effects.getFogColor())
                .waterColor(effects.getWaterColor())
                .waterFogColor(effects.getWaterFogColor())
                .skyColor(effects.getSkyColor())
                .grassColorModifier(effects.getGrassColorModifier())
        ;

        effects.getGrassColorOverride().ifPresent(builder::grassColorOverride);
        effects.getFoliageColorOverride().ifPresent(builder::foliageColorOverride);
        effects.getAmbientLoopSoundEvent().ifPresent(builder::ambientLoopSound);

        if (effects.getAmbientParticleSettings().isPresent()) {
            AmbientParticleSettings particle = effects.getAmbientParticleSettings().get();

            builder.ambientParticle(particle);
        }

        if (effects.getAmbientMoodSettings().isPresent()) {
            AmbientMoodSettings settings = effects.getAmbientMoodSettings().get();

            builder.ambientMoodSound(settings);
        }

        if (effects.getAmbientAdditionsSettings().isPresent()) {
            AmbientAdditionsSettings settings = effects.getAmbientAdditionsSettings().get();

            builder.ambientAdditionsSound(settings);
        }

        if (effects.getBackgroundMusic().isPresent()) {
            builder.backgroundMusic(effects.getBackgroundMusic().get());
        }

        return builder;
    }


    private Optional<WeightedList<Music>> backgroundMusic = Optional.empty();

    public BiomeSpecialEffects.Builder backgroundMusic(@Nullable Music backgroundMusic) {
        super.backgroundMusic(backgroundMusic);
        if (backgroundMusic == null) {
            this.backgroundMusic = Optional.empty();
            return this;
        } else {
            this.backgroundMusic = Optional.of(WeightedList.of(backgroundMusic));
            return this;
        }
    }

    public BiomeSpecialEffects.Builder backgroundMusic(WeightedList<Music> backgroundMusic) {
        super.backgroundMusic(backgroundMusic);
        this.backgroundMusic = Optional.of(backgroundMusic);
        return this;
    }


    public Optional<Holder<SoundEvent>>    ambientMoodSoundEvent = Optional.empty();
    public Optional<Integer>               ambientMoodTickDelay = Optional.empty();
    public Optional<Integer>               ambientMoodBlockSearchExtent = Optional.empty();
    public Optional<Double>                ambientMoodSoundPositionOffset = Optional.empty();

    public Optional<ParticleOptions>       ambientParticleParticleOptions = Optional.empty();
    public Optional<Float>                 ambientParticleProbability = Optional.empty();

    public Optional<Holder<SoundEvent>>    ambientAdditionsSoundEvent = Optional.empty();
    public Optional<Double>                ambientAdditionsTickChance = Optional.empty();

    public Optional<@org.jetbrains.annotations.Nullable Holder<SoundEvent>>    singleBackgroundMusicSoundEvent = Optional.empty();
    public Optional<Integer>               singleBackgroundMusicMinDelay = Optional.empty();
    public Optional<Integer>               singleBackgroundMusicMaxDelay = Optional.empty();
    public Optional<Boolean>               singleBackgroundMusicReplaceCurrentMusic = Optional.empty();

    private static final Field probabilityField;

    static{
        try{
            probabilityField = AmbientParticleSettings.class.getDeclaredField("probability");
            probabilityField.setAccessible(true);
        }catch(NoSuchFieldException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public BiomeSpecialEffects.@NotNull Builder ambientParticle(AmbientParticleSettings ambientParticle) {
        this.ambientParticleParticleOptions = Optional.of(ambientParticle.getOptions());
        try{
            this.ambientParticleProbability = Optional.of((Float)probabilityField.get(ambientParticle));
        }catch(IllegalAccessException e){
            throw new RuntimeException(e);
        }
        return this;
    }


    @Override
    public BiomeSpecialEffects.@NotNull Builder ambientMoodSound(AmbientMoodSettings ambientMoodSettings) {
        this.ambientMoodSoundEvent = Optional.of(ambientMoodSettings.getSoundEvent());
        this.ambientMoodTickDelay = Optional.of(ambientMoodSettings.getTickDelay());
        this.ambientMoodBlockSearchExtent = Optional.of(ambientMoodSettings.getBlockSearchExtent());
        this.ambientMoodSoundPositionOffset = Optional.of(ambientMoodSettings.getSoundPositionOffset());
        return this;
    }

    @Override
    public BiomeSpecialEffects.@NotNull Builder ambientAdditionsSound(AmbientAdditionsSettings ambientAdditionsSettings) {
        this.ambientAdditionsSoundEvent = Optional.of(ambientAdditionsSettings.getSoundEvent());
        this.ambientAdditionsTickChance = Optional.of(ambientAdditionsSettings.getTickChance());
        return this;
    }



    @Override
    public @NotNull BiomeSpecialEffects build(){

        if(
                ambientParticleParticleOptions.isPresent() &&
                ambientParticleProbability.isPresent()
        ){
            super.ambientParticle(new AmbientParticleSettings(ambientParticleParticleOptions.get(),ambientParticleProbability.get()));
        }

        if(
                ambientMoodSoundEvent.isPresent() &&
                ambientMoodTickDelay.isPresent() &&
                ambientMoodBlockSearchExtent.isPresent() &&
                ambientMoodSoundPositionOffset.isPresent()
        ){
            super.ambientMoodSound(new AmbientMoodSettings(ambientMoodSoundEvent.get(),ambientMoodTickDelay.get(),ambientMoodBlockSearchExtent.get(),ambientMoodSoundPositionOffset.get()));
        }

        if(
                ambientAdditionsSoundEvent.isPresent() &&
                ambientAdditionsTickChance.isPresent()
        ){
            super.ambientAdditionsSound(new AmbientAdditionsSettings(ambientAdditionsSoundEvent.get(),ambientAdditionsTickChance.get()));
        }

        if(
                singleBackgroundMusicSoundEvent.isPresent() ||
                singleBackgroundMusicMinDelay.isPresent() ||
                singleBackgroundMusicMaxDelay.isPresent() ||
                singleBackgroundMusicReplaceCurrentMusic.isPresent()
        ){
            if(

                    singleBackgroundMusicSoundEvent.isPresent() &&
                    singleBackgroundMusicMinDelay.isPresent() &&
                    singleBackgroundMusicMaxDelay.isPresent() &&
                    singleBackgroundMusicReplaceCurrentMusic.isPresent()
            ){
                super.backgroundMusic(new Music(singleBackgroundMusicSoundEvent.get(),singleBackgroundMusicMinDelay.get(),singleBackgroundMusicMaxDelay.get(),singleBackgroundMusicReplaceCurrentMusic.get()));
            }else if(this.backgroundMusic.isEmpty() || this.backgroundMusic.get().unwrap().size() != 1){
                //System.out.println("things are "+singleBackgroundMusicSoundEvent+" and "+singleBackgroundMusicMinDelay+" and "+singleBackgroundMusicMaxDelay+" and "+singleBackgroundMusicReplaceCurrentMusic);
                System.out.println("Base background music has invalid length for single overriding syntax / not enough valid configuration values provided for a single track");
            }else{
                var original = this.backgroundMusic.get().unwrap().getFirst().value();
                super.backgroundMusic(new Music(
                        singleBackgroundMusicSoundEvent.orElse(original.event()),
                        singleBackgroundMusicMinDelay.orElse(original.minDelay()),
                        singleBackgroundMusicMaxDelay.orElse(original.maxDelay()),
                        singleBackgroundMusicReplaceCurrentMusic.orElse(original.replaceCurrentMusic())
                ));
            }
        }

        return super.build();
    }
}
