package com.cleanroommc.multiblocked.common.capability;

import com.cleanroommc.multiblocked.api.capability.CapCapabilityProxy;
import com.cleanroommc.multiblocked.api.capability.IO;
import com.cleanroommc.multiblocked.api.capability.MultiblockCapability;
import com.cleanroommc.multiblocked.api.gui.texture.TextTexture;
import com.cleanroommc.multiblocked.api.gui.widget.imp.recipe.ContentWidget;
import com.cleanroommc.multiblocked.api.pattern.util.BlockInfo;
import com.cleanroommc.multiblocked.api.recipe.Recipe;
import com.cleanroommc.multiblocked.api.registry.MbdCapabilities;
import com.cleanroommc.multiblocked.common.capability.widget.NumberContentWidget;
import com.google.gson.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import java.awt.*;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FEMultiblockCapability extends MultiblockCapability<Integer> {

    public FEMultiblockCapability() {
        super("forge_energy", new Color(0xCB0000).getRGB());
    }

    @Override
    public Integer defaultContent() {
        return 500;
    }

    @Override
    public boolean isBlockHasCapability(@Nonnull IO io, @Nonnull TileEntity tileEntity) {
        return !getCapability(CapabilityEnergy.ENERGY, tileEntity).isEmpty();
    }

    @Override
    public Integer copyInner(Integer content) {
        return content;
    }

    @Override
    public FECapabilityProxy createProxy(@Nonnull IO io, @Nonnull TileEntity tileEntity) {
        return new FECapabilityProxy(tileEntity);
    }

    @Override
    public ContentWidget<? super Integer> createContentWidget() {
        return new NumberContentWidget().setContentTexture(new TextTexture("FE", color)).setUnit("FE");
    }

    @Override
    public BlockInfo[] getCandidates() {
        return new BlockInfo[0];
    }

    @Override
    public Integer deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return jsonElement.getAsInt();
    }

    @Override
    public JsonElement serialize(Integer integer, Type type, JsonSerializationContext jsonSerializationContext) {
        return new JsonPrimitive(integer);
    }

    public static class FECapabilityProxy extends CapCapabilityProxy<IEnergyStorage, Integer> {

        public FECapabilityProxy(TileEntity tileEntity) {
            super(MbdCapabilities.FE, tileEntity, CapabilityEnergy.ENERGY);
        }

        @Override
        protected List<Integer> handleRecipeInner(IO io, Recipe recipe, List<Integer> left, boolean simulate) {
            Set<IEnergyStorage> capabilities = getCapability();
            int sum = left.stream().reduce(0, Integer::sum);
            for (IEnergyStorage capability : capabilities) {
                if (io == IO.IN) {
                    sum = sum - capability.extractEnergy(sum, simulate);
                } else if (io == IO.OUT) {
                    sum = sum - capability.receiveEnergy(sum, simulate);
                }
                if (sum <= 0) break;
            }
            return sum <= 0 ? null : Collections.singletonList(sum);
        }

    }
}
