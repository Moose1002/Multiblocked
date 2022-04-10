package com.cleanroommc.multiblocked.common.capability;

import com.cleanroommc.multiblocked.api.capability.CapabilityProxy;
import com.cleanroommc.multiblocked.api.capability.IO;
import com.cleanroommc.multiblocked.api.capability.MultiblockCapability;
import com.cleanroommc.multiblocked.api.gui.widget.imp.recipe.ContentWidget;
import com.cleanroommc.multiblocked.api.pattern.util.BlockInfo;
import com.cleanroommc.multiblocked.api.recipe.Recipe;
import com.cleanroommc.multiblocked.common.capability.widget.ParticleStackWidget;
import com.google.gson.*;
import lach_01298.qmd.block.QMDBlocks;
import lach_01298.qmd.particle.ITileParticleStorage;
import lach_01298.qmd.particle.ParticleStack;
import lach_01298.qmd.particle.ParticleStorage;
import lach_01298.qmd.particle.Particles;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nonnull;
import java.awt.*;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;

public class ParticleQMDCapability extends MultiblockCapability<ParticleStack> {
    public static final ParticleQMDCapability CAP = new ParticleQMDCapability();

    private ParticleQMDCapability() {
        super("qmd_particle", new Color(0xCDD59DBC, true).getRGB());
    }

    @Override
    public ParticleStack defaultContent() {
        return new ParticleStack(Particles.antidown);
    }

    @Override
    public boolean isBlockHasCapability(@Nonnull IO io, @Nonnull TileEntity tileEntity) {
        return tileEntity instanceof ITileParticleStorage;
    }

    @Override
    public ParticleQMDCapabilityProxy createProxy(@Nonnull IO io, @Nonnull TileEntity tileEntity) {
        return new ParticleQMDCapabilityProxy(tileEntity);
    }

    @Override
    public ParticleStack copyInner(ParticleStack content) {
        return content.copy();
    }

    @Override
    public ContentWidget<ParticleStack> createContentWidget() {
        return new ParticleStackWidget();
    }

    @Override
    public BlockInfo[] getCandidates() {
        return new BlockInfo[]{
                BlockInfo.fromBlockState(QMDBlocks.acceleratorBeamPort.getDefaultState()),
                BlockInfo.fromBlockState(QMDBlocks.acceleratorSynchrotronPort.getDefaultState()),
                BlockInfo.fromBlockState(QMDBlocks.beamline.getDefaultState()),
                BlockInfo.fromBlockState(QMDBlocks.particleChamberBeamPort.getDefaultState()),
        };
    }

    @Override
    public ParticleStack deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObj = jsonElement.getAsJsonObject();
        return new ParticleStack(
                Particles.getParticleFromName(jsonObj.get("particle").getAsString()),
                jsonObj.get("amount").getAsInt(),
                jsonObj.get("energy").getAsLong(),
                jsonObj.get("focus").getAsDouble());
    }

    @Override
    public JsonElement serialize(ParticleStack particleStack, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject jsonObj = new JsonObject();
        jsonObj.addProperty("particle", particleStack.getParticle().getName());
        jsonObj.addProperty("amount", particleStack.getAmount());
        jsonObj.addProperty("focus", particleStack.getFocus());
        jsonObj.addProperty("energy", particleStack.getMeanEnergy());
        return jsonObj;
    }

    public static class ParticleQMDCapabilityProxy extends CapabilityProxy<ParticleStack> {

        public ParticleQMDCapabilityProxy(TileEntity tileEntity) {
            super(ParticleQMDCapability.CAP, tileEntity);
        }

        public ITileParticleStorage getCapability() {
            return (ITileParticleStorage)getTileEntity();
        }

        @Override
        protected List<ParticleStack> handleRecipeInner(IO io, Recipe recipe, List<ParticleStack> left, boolean simulate) {
            ITileParticleStorage capability = getCapability();
            if (capability == null) return left;
            Iterator<ParticleStack> iterator = left.iterator();
            if (io == IO.IN) {
                while (iterator.hasNext()) {
                    ParticleStack particleStack = iterator.next();
                    for (ParticleStorage storage : capability.getParticleBeams()) {
                        ParticleStack stored = storage.getParticleStack();
                        if (stored != null
                                && storage.canExtractParticle(null)
                                && stored.getParticle() == particleStack.getParticle()
                                && stored.getMeanEnergy() >= particleStack.getMeanEnergy()) {
                            int leftAmount = particleStack.getAmount() - stored.getAmount();
                            if (!simulate) {
                                storage.extractParticle(null, particleStack.getParticle(), particleStack.getAmount());
                            }
                            particleStack.setAmount(leftAmount);
                            if (leftAmount <= 0) {
                                iterator.remove();
                                break;
                            }
                        }
                    }
                }
            } else if (io == IO.OUT){
                while (iterator.hasNext()) {
                    ParticleStack particleStack = iterator.next();
                    for (ParticleStorage storage : capability.getParticleBeams()) {
                        if (storage.canReciveParticle(null, particleStack)) {
                            if (simulate) {
                                storage.reciveParticle(null, particleStack);
                            }
                            iterator.remove();
                            break;
                        }
                    }
                }
            }
            return left.isEmpty() ? null : left;
        }

    }
}
