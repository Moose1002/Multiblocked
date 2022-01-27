package io.github.cleanroommc.multiblocked.jei;

import io.github.cleanroommc.multiblocked.Multiblocked;
import io.github.cleanroommc.multiblocked.api.gui.modular.ModularUIGuiHandler;
import io.github.cleanroommc.multiblocked.api.gui.widget.imp.recipe.RecipeWidget;
import io.github.cleanroommc.multiblocked.api.recipe.RecipeMap;
import io.github.cleanroommc.multiblocked.jei.multipage.*;
import mezz.jei.Internal;
import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.config.Constants;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.input.IShowsRecipeFocuses;
import mezz.jei.input.InputHandler;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

@JEIPlugin
public class JeiPlugin implements IModPlugin {
    public static Field fieldRecipeLayout;
    static {
        try {
            fieldRecipeLayout = RecipeLayout.class.getDeclaredField("recipeWrapper");
            fieldRecipeLayout.setAccessible(true);
        } catch (NoSuchFieldException e) {
            Multiblocked.LOGGER.error(e);
        }
    }
    public static IJeiRuntime jeiRuntime;

    public static IRecipeWrapper getWrapper(RecipeLayout layout) {
        try {
            return (IRecipeWrapper)fieldRecipeLayout.get(layout);
        } catch (IllegalAccessException e) {
            Multiblocked.LOGGER.error(e);
        }
        return null;
    }

    @Override
    public void onRuntimeAvailable(@Nonnull IJeiRuntime jeiRuntime) {
        JeiPlugin.jeiRuntime = jeiRuntime;
    }

    @Override
    public void registerCategories(@Nonnull IRecipeCategoryRegistration registry) {
        IJeiHelpers jeiHelpers = registry.getJeiHelpers();
        registry.addRecipeCategories(new MultiblockInfoCategory(jeiHelpers));
        for (RecipeMap recipeMap : RecipeMap.RECIPE_MAP_REGISTRY.values()) {
            registry.addRecipeCategories(new RecipeMapCategory(jeiHelpers, recipeMap));
        }
    }

    @Override
    public void register(@Nonnull IModRegistry registry) {
        IJeiHelpers jeiHelpers = registry.getJeiHelpers();
        ModularUIGuiHandler modularUIGuiHandler = new ModularUIGuiHandler(jeiHelpers.recipeTransferHandlerHelper());
        registry.addAdvancedGuiHandlers(modularUIGuiHandler);
        registry.addGhostIngredientHandler(modularUIGuiHandler.getGuiContainerClass(), modularUIGuiHandler);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(modularUIGuiHandler, Constants.UNIVERSAL_RECIPE_TRANSFER_UID);
        for (RecipeMap recipeMap : RecipeMap.RECIPE_MAP_REGISTRY.values()) {
            registry.addRecipes(recipeMap.recipes.values().stream()
                            .map(RecipeWidget::new)
                            .map(RecipeWrapper::new)
                            .collect(Collectors.toList()),
                    Multiblocked.MODID + ":" + recipeMap.name);
        }
        MultiblockInfoCategory.registerRecipes(registry);
    }

    public static void setupInputHandler() {
        try {
            Field inputHandlerField = Internal.class.getDeclaredField("inputHandler");
            inputHandlerField.setAccessible(true);
            InputHandler inputHandler = (InputHandler) inputHandlerField.get(null);
            List<IShowsRecipeFocuses> showsRecipeFocuses = ObfuscationReflectionHelper.getPrivateValue(InputHandler.class, inputHandler, "showsRecipeFocuses");
            showsRecipeFocuses.add(new MultiblockInfoRecipeFocusShower());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
