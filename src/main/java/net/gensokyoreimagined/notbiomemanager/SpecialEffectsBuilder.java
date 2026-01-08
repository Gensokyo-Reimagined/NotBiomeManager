package net.gensokyoreimagined.notbiomemanager;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.core.particles.ParticleOptions;

import java.lang.reflect.Field;
import java.util.Optional;

public class SpecialEffectsBuilder {

    private int waterColor;
    private Optional<Integer> foliageColorOverride = Optional.empty();
    private Optional<Integer> dryFoliageColorOverride = Optional.empty();
    private Optional<Integer> grassColorOverride = Optional.empty();
    private BiomeSpecialEffects.GrassColorModifier grassColorModifier = BiomeSpecialEffects.GrassColorModifier.NONE;

    private int attributeFogColor = -1;
    private int attributeSkyColor = -1;
    private int attributeWaterFogColor = -1;

    public Optional<ParticleOptions> ambientParticleParticleOptions = Optional.empty();
    public Optional<Float> ambientParticleProbability = Optional.empty();
    public Optional<Holder<SoundEvent>> ambientMoodSoundEvent = Optional.empty();
    public Optional<Integer> ambientMoodTickDelay = Optional.empty();
    public Optional<Integer> ambientMoodBlockSearchExtent = Optional.empty();
    public Optional<Double> ambientMoodSoundPositionOffset = Optional.empty();
    public Optional<Holder<SoundEvent>> ambientAdditionsSoundEvent = Optional.empty();
    public Optional<Double> ambientAdditionsTickChance = Optional.empty();
    public Optional<Holder<SoundEvent>> singleBackgroundMusicSoundEvent = Optional.empty();
    public Optional<Integer> singleBackgroundMusicMinDelay = Optional.empty();
    public Optional<Integer> singleBackgroundMusicMaxDelay = Optional.empty();
    public Optional<Boolean> singleBackgroundMusicReplaceCurrentMusic = Optional.empty();

    public static SpecialEffectsBuilder getSpecialEffects(Biome biome) {
        SpecialEffectsBuilder builder = new SpecialEffectsBuilder();

        BiomeSpecialEffects effects = biome.getSpecialEffects();

        builder.waterColor = effects.waterColor();
        builder.foliageColorOverride = effects.foliageColorOverride();
        builder.grassColorOverride = effects.grassColorOverride();
        builder.grassColorModifier = effects.grassColorModifier();

        builder.dryFoliageColorOverride = effects.dryFoliageColorOverride();

        return builder;
    }

    public SpecialEffectsBuilder fogColor(int color) {
        this.attributeFogColor = color;
        return this;
    }

    public SpecialEffectsBuilder waterColor(int color) {
        this.waterColor = color;
        return this;
    }

    public SpecialEffectsBuilder waterFogColor(int color) {
        this.attributeWaterFogColor = color;
        return this;
    }

    public SpecialEffectsBuilder skyColor(int color) {
        this.attributeSkyColor = color;
        return this;
    }

    public SpecialEffectsBuilder foliageColorOverride(int color) {
        this.foliageColorOverride = Optional.of(color);
        return this;
    }

    public SpecialEffectsBuilder dryFoliageColorOverride(int color) {
        this.dryFoliageColorOverride = Optional.of(color);
        return this;
    }

    public SpecialEffectsBuilder grassColorOverride(int color) {
        this.grassColorOverride = Optional.of(color);
        return this;
    }

    public SpecialEffectsBuilder grassColorModifier(BiomeSpecialEffects.GrassColorModifier modifier) {
        this.grassColorModifier = modifier;
        return this;
    }

    public SpecialEffectsBuilder ambientLoopSound(Holder<SoundEvent> sound) {
        // I DONT CARE ENOUGH TO DO THIS
        return this;
    }

    public BiomeSpecialEffects build() {
        var builder = new BiomeSpecialEffects.Builder();

        builder.waterColor(this.waterColor);

        this.foliageColorOverride.ifPresent(builder::foliageColorOverride);
        this.grassColorOverride.ifPresent(builder::grassColorOverride);
        builder.grassColorModifier(this.grassColorModifier);

        this.dryFoliageColorOverride.ifPresent(builder::dryFoliageColorOverride);

        return builder.build();
    }

    public EnvironmentAttributeMap buildAttributes() {
        var builder = EnvironmentAttributeMap.builder();

        // Set standard attributes
        if (this.attributeFogColor != -1) {
            builder.set(EnvironmentAttributes.FOG_COLOR, this.attributeFogColor);
        }
        if (this.attributeSkyColor != -1) {
            builder.set(EnvironmentAttributes.SKY_COLOR, this.attributeSkyColor);
        }
        if (this.attributeWaterFogColor != -1) {
            builder.set(EnvironmentAttributes.WATER_FOG_COLOR, this.attributeWaterFogColor);
        }

        return builder.build();
    }
}
