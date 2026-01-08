package net.gensokyoreimagined.notbiomemanager;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.AmbientParticle;
import net.minecraft.core.particles.ParticleOptions;
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

    private int attributeCloudColor = -1;
    private Optional<Double> attributeCloudHeight = Optional.empty();
    private Optional<Double> attributeCloudFogEndDistance = Optional.empty();
    private Optional<Double> attributeFogStartDistance = Optional.empty();
    private Optional<Double> attributeFogEndDistance = Optional.empty();
    private Optional<Double> attributeSkyFogEndDistance = Optional.empty();
    private int attributeSkyLightColor = -1;
    private Optional<Double> attributeSkyLightFactor = Optional.empty();
    private int attributeSunriseSunsetColor = -1;
    private Optional<Double> attributeStarBrightness = Optional.empty();

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
        return this;
    }

    // New Builders
    public SpecialEffectsBuilder cloudColor(int color) {
        this.attributeCloudColor = color;
        return this;
    }

    public SpecialEffectsBuilder cloudHeight(double height) {
        this.attributeCloudHeight = Optional.of(height);
        return this;
    }

    public SpecialEffectsBuilder cloudFogEndDistance(double distance) {
        this.attributeCloudFogEndDistance = Optional.of(distance);
        return this;
    }

    public SpecialEffectsBuilder fogStartDistance(double distance) {
        this.attributeFogStartDistance = Optional.of(distance);
        return this;
    }

    public SpecialEffectsBuilder fogEndDistance(double distance) {
        this.attributeFogEndDistance = Optional.of(distance);
        return this;
    }

    public SpecialEffectsBuilder skyFogEndDistance(double distance) {
        this.attributeSkyFogEndDistance = Optional.of(distance);
        return this;
    }

    public SpecialEffectsBuilder skyLightColor(int color) {
        this.attributeSkyLightColor = color;
        return this;
    }

    public SpecialEffectsBuilder skyLightFactor(double factor) {
        this.attributeSkyLightFactor = Optional.of(factor);
        return this;
    }

    public SpecialEffectsBuilder sunriseSunsetColor(int color) {
        this.attributeSunriseSunsetColor = color;
        return this;
    }

    public SpecialEffectsBuilder starBrightness(double brightness) {
        this.attributeStarBrightness = Optional.of(brightness);
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

        if (this.attributeFogColor != -1)
            builder.set(EnvironmentAttributes.FOG_COLOR, this.attributeFogColor);
        if (this.attributeSkyColor != -1)
            builder.set(EnvironmentAttributes.SKY_COLOR, this.attributeSkyColor);
        if (this.attributeWaterFogColor != -1)
            builder.set(EnvironmentAttributes.WATER_FOG_COLOR, this.attributeWaterFogColor);
        if (this.attributeCloudColor != -1)
            builder.set(EnvironmentAttributes.CLOUD_COLOR, this.attributeCloudColor);
        this.attributeCloudHeight.ifPresent(v -> builder.set(EnvironmentAttributes.CLOUD_HEIGHT, v.floatValue())); // Assuming
                                                                                                                   // float
        this.attributeCloudFogEndDistance
                .ifPresent(v -> builder.set(EnvironmentAttributes.CLOUD_FOG_END_DISTANCE, v.floatValue()));
        this.attributeFogStartDistance
                .ifPresent(v -> builder.set(EnvironmentAttributes.FOG_START_DISTANCE, v.floatValue()));
        this.attributeFogEndDistance
                .ifPresent(v -> builder.set(EnvironmentAttributes.FOG_END_DISTANCE, v.floatValue()));
        this.attributeSkyFogEndDistance
                .ifPresent(v -> builder.set(EnvironmentAttributes.SKY_FOG_END_DISTANCE, v.floatValue()));
        if (this.attributeSkyLightColor != -1)
            builder.set(EnvironmentAttributes.SKY_LIGHT_COLOR, this.attributeSkyLightColor);
        this.attributeSkyLightFactor
                .ifPresent(v -> builder.set(EnvironmentAttributes.SKY_LIGHT_FACTOR, v.floatValue()));
        if (this.attributeSunriseSunsetColor != -1)
            builder.set(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, this.attributeSunriseSunsetColor);
        this.attributeStarBrightness.ifPresent(v -> builder.set(EnvironmentAttributes.STAR_BRIGHTNESS, v.floatValue()));

        if (this.ambientParticleParticleOptions.isPresent()) {
            builder.set(EnvironmentAttributes.AMBIENT_PARTICLES,
                    java.util.List.of(new AmbientParticle(this.ambientParticleParticleOptions.get(),
                            this.ambientParticleProbability.orElse(0.01f))));
        }

        return builder.build();
    }
}
