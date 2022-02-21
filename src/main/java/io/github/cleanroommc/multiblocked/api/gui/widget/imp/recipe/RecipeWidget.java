package io.github.cleanroommc.multiblocked.api.gui.widget.imp.recipe;

import com.google.common.collect.ImmutableList;
import io.github.cleanroommc.multiblocked.api.capability.IO;
import io.github.cleanroommc.multiblocked.api.capability.MultiblockCapability;
import io.github.cleanroommc.multiblocked.api.gui.texture.ColorRectTexture;
import io.github.cleanroommc.multiblocked.api.gui.texture.IGuiTexture;
import io.github.cleanroommc.multiblocked.api.gui.texture.ResourceTexture;
import io.github.cleanroommc.multiblocked.api.gui.widget.WidgetGroup;
import io.github.cleanroommc.multiblocked.api.gui.widget.imp.DraggableScrollableWidgetGroup;
import io.github.cleanroommc.multiblocked.api.gui.widget.imp.LabelWidget;
import io.github.cleanroommc.multiblocked.api.recipe.Recipe;
import net.minecraft.client.resources.I18n;

import java.util.Map;
import java.util.function.DoubleSupplier;

public class RecipeWidget extends WidgetGroup {
    public final Recipe recipe;

    public RecipeWidget(Recipe recipe, ResourceTexture progress, IGuiTexture background) {
        this(recipe, ProgressWidget.JEIProgress, progress, background);
    }

    public RecipeWidget(Recipe recipe, ResourceTexture progress) {
        this(recipe, ProgressWidget.JEIProgress, progress, new ColorRectTexture(0x1f000000));
    }

    public RecipeWidget(Recipe recipe, DoubleSupplier doubleSupplier, ResourceTexture progress, IGuiTexture background) {
        super(0, 0, 176, 84);
        this.recipe = recipe;
        setClientSideWidget();
        DraggableScrollableWidgetGroup inputs = new DraggableScrollableWidgetGroup(5, 5, 64, 64).setBackground(background);
        DraggableScrollableWidgetGroup outputs = new DraggableScrollableWidgetGroup(176 - 64 - 5, 5, 64, 64).setBackground(background);
        this.addWidget(inputs);
        this.addWidget(outputs);
        this.addWidget(new ProgressWidget(doubleSupplier, 78, 27, 20, 20, progress));
        this.addWidget(new LabelWidget(5, 73, () -> I18n.format("multiblocked.recipe.duration", this.recipe.duration / 20.)));
        int index = 0;
        for (Map.Entry<MultiblockCapability<?>, ImmutableList<Object>> entry : recipe.inputs.entrySet()) {
            MultiblockCapability<?> capability = entry.getKey();
            for (Object o : entry.getValue()) {
                inputs.addWidget(capability.createContentWidget().setContent(IO.IN, o).setSelfPosition(2 + 20 * (index % 3), 2 + 20 * (index / 3)));
                index++;
            }
        }

        index = 0;
        for (Map.Entry<MultiblockCapability<?>, ImmutableList<Object>> entry : recipe.outputs.entrySet()) {
            MultiblockCapability<?> capability = entry.getKey();
            for (Object o : entry.getValue()) {
                outputs.addWidget(capability.createContentWidget().setContent(IO.OUT, o).setSelfPosition(2 + 20 * (index % 3), 2 + 20 * (index / 3)));
                index++;
            }
        }
    }
}