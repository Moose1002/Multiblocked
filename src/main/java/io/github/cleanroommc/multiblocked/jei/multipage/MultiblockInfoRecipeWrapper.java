package io.github.cleanroommc.multiblocked.jei.multipage;

import io.github.cleanroommc.multiblocked.Multiblocked;
import io.github.cleanroommc.multiblocked.api.definition.ControllerDefinition;
import io.github.cleanroommc.multiblocked.api.pattern.BlockInfo;
import io.github.cleanroommc.multiblocked.api.pattern.MultiblockShapeInfo;
import io.github.cleanroommc.multiblocked.api.pattern.MultiblockState;
import io.github.cleanroommc.multiblocked.api.pattern.TraceabilityPredicate;
import io.github.cleanroommc.multiblocked.api.tile.ControllerTileEntity;
import io.github.cleanroommc.multiblocked.client.renderer.scene.ImmediateWorldSceneRenderer;
import io.github.cleanroommc.multiblocked.client.renderer.scene.WorldSceneRenderer;
import io.github.cleanroommc.multiblocked.client.util.RenderUtils;
import io.github.cleanroommc.multiblocked.client.util.TrackedDummyWorld;
import io.github.cleanroommc.multiblocked.util.ItemStackKey;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.gui.recipes.RecipeLayout;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.util.ITooltipFlag.TooltipFlags;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MultiblockInfoRecipeWrapper implements IRecipeWrapper {
    private static final int MAX_PARTS = 18;
    private static final int PARTS_HEIGHT = 36;
    private final int SLOT_SIZE = 18;
    private final int SLOTS_PER_ROW = 9;
    private final int ICON_SIZE = 20;
    private final int RIGHT_PADDING = 5;

    private static class MBPattern {
        final WorldSceneRenderer sceneRenderer;
        final List<ItemStack> parts;
        final Map<BlockPos, TraceabilityPredicate> predicateMap;

        public MBPattern(final WorldSceneRenderer sceneRenderer, final List<ItemStack> parts, Map<BlockPos, TraceabilityPredicate> predicateMap) {
            this.sceneRenderer = sceneRenderer;
            this.parts = parts;
            this.predicateMap = predicateMap;
        }
    }

    private final ControllerDefinition controllerDefinition;
    private final MBPattern[] patterns;
    private final Map<GuiButton, Runnable> buttons = new HashMap<>();
    private RecipeLayout recipeLayout;
    private final List<ItemStack> allItemStackInputs = new ArrayList<>();

    private int layerIndex = -1;
    private int currentRendererPage = 0;
    private int lastMouseX;
    private int lastMouseY;
    private Vector3f center;
    private float rotationYaw;
    private float rotationPitch;
    private float zoom;

    private final GuiButton buttonPreviousPattern;
    private final GuiButton buttonNextPattern;
    private final GuiButton nextLayerButton;

    private IDrawable slot;
    private boolean drawInfoIcon;
    private static ItemStack tooltipBlockStack;
    private static long lastRender;
    private List<String> predicateTips;

    private BlockPos selected;
    private final List<TraceabilityPredicate.SimplePredicate> predicates;
    private TraceabilityPredicate father;

    public MultiblockInfoRecipeWrapper(ControllerDefinition controllerDefinition) {
        this.controllerDefinition = controllerDefinition;
        HashSet<ItemStackKey> drops = new HashSet<>();
        drops.add(new ItemStackKey(this.controllerDefinition.getStackForm()));
        this.patterns = controllerDefinition.getDesigns().stream()
                .map(it -> initializePattern(it, drops))
                .toArray(MBPattern[]::new);
        drops.forEach(it -> allItemStackInputs.add(it.getItemStack()));
        this.nextLayerButton = new GuiButton(0, 176 - (ICON_SIZE + RIGHT_PADDING), 70, ICON_SIZE, ICON_SIZE, "");
        this.buttonPreviousPattern = new GuiButton(0, 176 - ((2 * ICON_SIZE) + RIGHT_PADDING + 1), 90, ICON_SIZE, ICON_SIZE, "<");
        this.buttonNextPattern = new GuiButton(0, 176 - (ICON_SIZE + RIGHT_PADDING), 90, ICON_SIZE, ICON_SIZE, ">");
        this.buttons.put(nextLayerButton, this::toggleNextLayer);
        this.buttons.put(buttonPreviousPattern, () -> switchRenderPage(-1));
        this.buttons.put(buttonNextPattern, () -> switchRenderPage(1));
        boolean isPagesDisabled = patterns.length == 1;
        this.buttonPreviousPattern.visible = !isPagesDisabled;
        this.buttonNextPattern.visible = !isPagesDisabled;
        this.predicates = new ArrayList<>();
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputs(VanillaTypes.ITEM, allItemStackInputs);
        ingredients.setOutput(VanillaTypes.ITEM, controllerDefinition.getStackForm());
    }

    private static MultiblockInfoRecipeWrapper lastWrapper;

    public void setRecipeLayout(RecipeLayout layout, IGuiHelper guiHelper) {
        this.recipeLayout = layout;

        this.slot = guiHelper.drawableBuilder(new ResourceLocation(Multiblocked.MODID, "textures/test/d.png"), 0, 0, SLOT_SIZE, SLOT_SIZE).setTextureSize(SLOT_SIZE, SLOT_SIZE).build();

        IDrawable border = layout.getRecipeCategory().getBackground();
        preparePlaceForParts(border.getHeight());
        if (Mouse.getEventDWheel() == 0 || lastWrapper != this) {
            selected = null;
            this.predicates.clear();
            this.father = null;
            lastWrapper = this;
            this.nextLayerButton.x = border.getWidth() - (ICON_SIZE + RIGHT_PADDING);
            this.buttonPreviousPattern.x = border.getWidth() - ((2 * ICON_SIZE) + RIGHT_PADDING + 1);
            this.buttonNextPattern.x = border.getWidth() - (ICON_SIZE + RIGHT_PADDING);
            this.buttonPreviousPattern.enabled = false;
            this.buttonNextPattern.enabled = patterns.length > 1;
            Vector3f size = ((TrackedDummyWorld) getCurrentRenderer().world).getSize();
            float max = Math.max(Math.max(Math.max(size.x, size.y), size.z), 1);
            this.zoom = (float) (3.5 * Math.sqrt(max));
            this.rotationYaw = 20.0f;
            this.rotationPitch = 50f;
            this.currentRendererPage = 0;
            setNextLayer(-1);
        } else {
            zoom = (float) MathHelper.clamp(zoom + (Mouse.getEventDWheel() < 0 ? 0.5 : -0.5), 3, 999);
            setNextLayer(getLayerIndex());
            if (predicates != null && predicates.size() > 0) {
                setItemStackGroup();
            }
        }
        if (getCurrentRenderer() != null) {
            TrackedDummyWorld world = (TrackedDummyWorld) getCurrentRenderer().world;
            resetCenter(world);
        }
        updateParts();
    }

    public WorldSceneRenderer getCurrentRenderer() {
        return patterns[currentRendererPage].sceneRenderer;
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    private void toggleNextLayer() {
        WorldSceneRenderer renderer = getCurrentRenderer();
        int height = (int) ((TrackedDummyWorld)renderer.world).getSize().getY() - 1;
        if (++this.layerIndex > height) {
            //if current layer index is more than max height, reset it
            //to display all layers
            this.layerIndex = -1;
        }
        setNextLayer(layerIndex);
    }

    private void setNextLayer(int newLayer) {
        this.layerIndex = newLayer;
        this.nextLayerButton.displayString = "L:" + (layerIndex == -1 ? "A" : Integer.toString(layerIndex + 1));
        WorldSceneRenderer renderer = getCurrentRenderer();
        if (renderer != null) {
            TrackedDummyWorld world = ((TrackedDummyWorld)renderer.world);
            resetCenter(world);
            renderer.renderedBlocksMap.clear();
            int minY = (int) world.getMinPos().getY();
            Collection<BlockPos> renderBlocks;
            if (newLayer == -1) {
                renderBlocks = world.renderedBlocks;
            } else {
                renderBlocks = world.renderedBlocks.stream().filter(pos->pos.getY() - minY == newLayer).collect(Collectors.toSet());
            }
            renderer.addRenderedBlocks(renderBlocks, null);
        }
    }

    private void resetCenter(TrackedDummyWorld world) {
        Vector3f size = world.getSize();
        Vector3f minPos = world.getMinPos();
        center = new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2);
        getCurrentRenderer().setCameraLookAt(center, zoom, Math.toRadians(rotationPitch), Math.toRadians(rotationYaw));
    }

    private void switchRenderPage(int amount) {
        int maxIndex = patterns.length - 1;
        int newIndex = Math.max(0, Math.min(currentRendererPage + amount, maxIndex));
        if (currentRendererPage != newIndex) {
            this.currentRendererPage = newIndex;
            this.buttonNextPattern.enabled = newIndex < maxIndex;
            this.buttonPreviousPattern.enabled = newIndex > 0;
            setNextLayer(-1);
            updateParts();
            getCurrentRenderer().setCameraLookAt(center, zoom, Math.toRadians(rotationPitch), Math.toRadians(rotationYaw));
            if (this.selected != null) {
                this.selected = null;
                for (int i = 0; i < predicates.size(); i++) {
                    recipeLayout.getItemStacks().set(i + MAX_PARTS, ItemStack.EMPTY);
                }
                predicates.clear();
                this.father = null;
            }
        }
    }

    private void preparePlaceForParts(int recipeHeight) {
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        for (int i = 0; i < MAX_PARTS; ++i)
            itemStackGroup.init(i, true, SLOT_SIZE * i - (SLOT_SIZE * SLOTS_PER_ROW) * (i / SLOTS_PER_ROW) + (SLOT_SIZE/ 2) - 2, recipeHeight - PARTS_HEIGHT + SLOT_SIZE * (i / SLOTS_PER_ROW));
    }

    private void updateParts() {
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        List<ItemStack> parts = this.patterns[currentRendererPage].parts;
        int limit = Math.min(parts.size(), MAX_PARTS);
        for (int i = 0; i < limit; ++i)
            itemStackGroup.set(i, parts.get(i));
        for (int i = parts.size(); i < limit; ++i)
            itemStackGroup.set(i, (ItemStack) null);
    }

    @Override
    public void drawInfo(@Nonnull Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        WorldSceneRenderer renderer = getCurrentRenderer();
        int sceneHeight = recipeHeight - PARTS_HEIGHT;

        renderer.render(recipeLayout.getPosX(), recipeLayout.getPosY(), recipeWidth, sceneHeight, mouseX + recipeLayout.getPosX(), mouseY + recipeLayout.getPosY());
        drawMultiblockName(recipeWidth);

        //reset colors (so any elements render after this point are not dark)
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);


        int iconX = recipeWidth - (ICON_SIZE + RIGHT_PADDING);
        int iconY = 49;

        this.drawInfoIcon = iconX <= mouseX && mouseX <= iconX + ICON_SIZE && iconY <= mouseY && mouseY <= iconY + ICON_SIZE;

        // draw parts slots
        for (int i = 0; i < MAX_PARTS; ++i) {
            this.slot.draw(minecraft, SLOT_SIZE * i - (SLOTS_PER_ROW * SLOT_SIZE) * (i / SLOTS_PER_ROW) + (SLOT_SIZE / 2) - 2, sceneHeight + SLOT_SIZE * (i / SLOTS_PER_ROW));
        }

        // draw candidates slots
        for (int i = 0; i < predicates.size(); i++) {
            this.slot.draw(minecraft, 5 + (i / 6) * SLOT_SIZE, (i % 6) * SLOT_SIZE + 10);
        }

        // draw buttons
        for (GuiButton button : buttons.keySet()) {
            button.drawButton(minecraft, mouseX, mouseY, 0.0f);
        }

        tooltipBlockStack = null;
        this.predicateTips = null;
        RayTraceResult rayTraceResult = renderer.getLastTraceResult();
        boolean insideView = mouseX >= 0 && mouseY >= 0 &&
                mouseX < recipeWidth && mouseY < sceneHeight;
        boolean leftClickHeld = Mouse.isButtonDown(0);
        boolean rightClickHeld = Mouse.isButtonDown(1);
        if (insideView) {
            for (GuiButton button : buttons.keySet()) {
                if (button.isMouseOver()) {
                    insideView = false;
                    break;
                }
            }
        }
        if (insideView) {
            if (leftClickHeld) {
                rotationPitch += mouseX - lastMouseX + 360;
                rotationPitch = rotationPitch % 360;
                rotationYaw = (float) MathHelper.clamp(rotationYaw + (mouseY - lastMouseY), -89.9, 89.9);
            } else if (rightClickHeld) {
                int mouseDeltaY = mouseY - lastMouseY;
                if (Math.abs(mouseDeltaY) > 1) {
                    this.zoom = (float) MathHelper.clamp(zoom + (mouseDeltaY > 0 ? 0.5 : -0.5), 3, 999);
                }
            }
            renderer.setCameraLookAt(center, zoom, Math.toRadians(rotationPitch), Math.toRadians(rotationYaw));
        }

        if (!(leftClickHeld || rightClickHeld) && rayTraceResult != null && !renderer.world.isAirBlock(rayTraceResult.getBlockPos())) {
            IBlockState blockState = renderer.world.getBlockState(rayTraceResult.getBlockPos());
            ItemStack itemStack = blockState.getBlock().getPickBlock(blockState, rayTraceResult, renderer.world, rayTraceResult.getBlockPos(), minecraft.player);
            TraceabilityPredicate predicates = patterns[currentRendererPage].predicateMap.get(rayTraceResult.getBlockPos());
            if (predicates != null) {
                MultiblockState worldState = new MultiblockState(renderer.world, null);
                worldState.clean();
                worldState.update(rayTraceResult.getBlockPos(), predicates);
                for (TraceabilityPredicate.SimplePredicate common : predicates.common) {
                    if (common.test(worldState)) {
                        predicateTips = common.getToolTips(predicates);
                        break;
                    }
                }
                if (predicateTips == null) {
                    for (TraceabilityPredicate.SimplePredicate limit : predicates.limited) {
                        if (limit.test(worldState)) {
                            predicateTips = limit.getToolTips(predicates);
                            break;
                        }
                    }
                }
            }
            if (!itemStack.isEmpty()) {
                tooltipBlockStack = itemStack;
            }
        }

        lastRender = System.currentTimeMillis();
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        RenderHelper.disableStandardItemLighting();
    }

    private void drawMultiblockName(int recipeWidth) {
        String localizedName = I18n.format(controllerDefinition.location.getPath() + ".name");
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        List<String> lines = fontRenderer.listFormattedStringToWidth(localizedName, recipeWidth - 10);
        for (int i = 0; i < lines.size(); i++) {
            fontRenderer.drawString(lines.get(i), (recipeWidth - fontRenderer.getStringWidth(lines.get(i))) / 2, fontRenderer.FONT_HEIGHT * i, 0x333333);
        }
    }

    @Override
    public boolean handleClick(@Nonnull Minecraft minecraft, int mouseX, int mouseY, int mouseButton) {
        for (Entry<GuiButton, Runnable> button : buttons.entrySet()) {
            if (button.getKey().mousePressed(minecraft, mouseX, mouseY)) {
                button.getValue().run();
                selected = null;
                return true;
            }
        }
        if (mouseButton == 1) {
            if (getCurrentRenderer().getLastTraceResult() == null) {
                if (this.selected != null) {
                    this.selected = null;
                    for (int i = 0; i < predicates.size(); i++) {
                        recipeLayout.getItemStacks().set(i + MAX_PARTS, ItemStack.EMPTY);
                    }
                    predicates.clear();
                    this.father = null;
                    return true;
                }
                return false;
            }
            BlockPos selected = getCurrentRenderer().getLastTraceResult().getBlockPos();
            if (!Objects.equals(this.selected, selected)) {
                for (int i = 0; i < predicates.size(); i++) {
                    recipeLayout.getItemStacks().set(i + MAX_PARTS, ItemStack.EMPTY);
                }
                predicates.clear();
                this.father = null;
                this.selected = selected;
                TraceabilityPredicate predicate = patterns[currentRendererPage].predicateMap.get(this.selected);
                if (predicate!= null) {
                    predicates.addAll(predicate.common);
                    predicates.addAll(predicate.limited);
                    predicates.removeIf(p -> p.candidates == null);
                    this.father = predicate;
                    setItemStackGroup();
                }
                return true;
            }
        }
        return false;
    }


    private void setItemStackGroup() {
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        for (int i = 0; i < predicates.size(); i++) {
            itemStackGroup.init(i + MAX_PARTS, true, 5 + (i / 6) * SLOT_SIZE, (i % 6) * SLOT_SIZE + 10);
            itemStackGroup.set(i + MAX_PARTS, predicates.get(i).getCandidates());
        }

        itemStackGroup.addTooltipCallback((slotIndex, input, itemStack, tooltip)->{
            if (slotIndex >= MAX_PARTS && slotIndex < MAX_PARTS + predicates.size()) {
                tooltip.addAll(predicates.get(slotIndex - MAX_PARTS).getToolTips(father));
            }
        });
    }

    @Nonnull
    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (drawInfoIcon) {
            return Arrays.asList(I18n.format("gregtech.multiblock.preview.zoom"), I18n.format("gregtech.multiblock.preview.rotate"), I18n.format("gregtech.multiblock.preview.select"));
        } else if (tooltipBlockStack != null && !tooltipBlockStack.isEmpty() && !Mouse.isButtonDown(0)) {
            Minecraft minecraft = Minecraft.getMinecraft();
            ITooltipFlag flag = minecraft.gameSettings.advancedItemTooltips ? TooltipFlags.ADVANCED : TooltipFlags.NORMAL;
            List<String> tooltip = tooltipBlockStack.getTooltip(minecraft.player, flag);
            EnumRarity rarity = tooltipBlockStack.getRarity();
            for (int k = 0; k < tooltip.size(); ++k) {
                if (k == 0) {
                    tooltip.set(k, rarity.color + tooltip.get(k));
                } else {
                    tooltip.set(k, TextFormatting.GRAY + tooltip.get(k));
                }
            }
            if (predicateTips != null) {
                tooltip.addAll(predicateTips);
            }
            return tooltip;
        }
        return Collections.emptyList();
    }

    private static class PartInfo {
        final ItemStackKey itemStackKey;
        boolean isController = false;
        boolean isTile = false;
        final int blockId;
        int amount = 0;

        PartInfo(final ItemStackKey itemStackKey, final BlockInfo blockInfo) {
            this.itemStackKey = itemStackKey;
            this.blockId = Block.getIdFromBlock(blockInfo.getBlockState().getBlock());
            TileEntity tileEntity = blockInfo.getTileEntity();
            if (tileEntity != null) {
                this.isTile = true;
                if (tileEntity instanceof ControllerTileEntity)
                    this.isController = true;
            }
        }

        ItemStack getItemStack() {
            ItemStack result = this.itemStackKey.getItemStack();
            result.setCount(this.amount);
            return result;
        }
    }

    private Map<ItemStackKey, PartInfo> gatherBlockDrops(World world, Map<BlockPos, BlockInfo> blocks, Set<ItemStackKey> drops) {
        Map<ItemStackKey, PartInfo> partsMap = new HashMap<>();
        NonNullList<ItemStack> dropsList = NonNullList.create();
        for (Entry<BlockPos, BlockInfo> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            IBlockState blockState = world.getBlockState(pos);
            NonNullList<ItemStack> blockDrops = NonNullList.create();
            blockState.getBlock().getDrops(blockDrops, world, pos, blockState, 0);
            dropsList.addAll(blockDrops);

            for (ItemStack itemStack : blockDrops) {
                ItemStackKey itemStackKey = new ItemStackKey(itemStack);
                PartInfo partInfo = partsMap.get(itemStackKey);
                if (partInfo == null) {
                    partInfo = new PartInfo(itemStackKey, entry.getValue());
                    partsMap.put(itemStackKey, partInfo);
                }
                ++partInfo.amount;
            }
        }
        for (ItemStack itemStack : dropsList) {
            drops.add(new ItemStackKey(itemStack));
        }
        return partsMap;
    }


    private MBPattern initializePattern(MultiblockShapeInfo shapeInfo, Set<ItemStackKey> blockDrops) {
        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        ControllerTileEntity controllerBase = null;
        BlockInfo[][][] blocks = shapeInfo.getBlocks();
        TrackedDummyWorld world = new TrackedDummyWorld();
        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    TileEntity tileEntity = column[z].getTileEntity();
                    IBlockState blockState = column[z].getBlockState();
                    if (tileEntity == null && blockState.getBlock().hasTileEntity(blockState)) {
                        tileEntity = blockState.getBlock().createTileEntity(world, blockState);
                    }
                    if (tileEntity instanceof ControllerTileEntity) {
                        controllerBase = (ControllerTileEntity) tileEntity;
                    }
                    blockMap.put(new BlockPos(x, y, z), new BlockInfo(blockState, tileEntity));
                }
            }
        }
        ImmediateWorldSceneRenderer worldSceneRenderer = new ImmediateWorldSceneRenderer(world);
        worldSceneRenderer.setClearColor(0xC6C6C6);
        world.addBlocks(blockMap);
        Vector3f size = world.getSize();
        Vector3f minPos = world.getMinPos();
        center = new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2);
        worldSceneRenderer.addRenderedBlocks(world.renderedBlocks, null);
        worldSceneRenderer.setOnLookingAt(ray -> {});
        worldSceneRenderer.setAfterWorldRender(renderer -> {
            BlockPos look = worldSceneRenderer.getLastTraceResult() == null ? null : worldSceneRenderer.getLastTraceResult().getBlockPos();
            if (look != null && look.equals(selected)) {
                renderBlockOverLay(selected, 200, 75, 75);
                return;
            }
            renderBlockOverLay(look, 150, 150, 150);
            renderBlockOverLay(selected, 255, 0, 0);
        });
        world.setRenderFilter(pos->worldSceneRenderer.renderedBlocksMap.keySet().stream().anyMatch(c->c.contains(pos)));
        List<ItemStack> parts = gatherBlockDrops(worldSceneRenderer.world, blockMap, blockDrops).values().stream().sorted((one, two) -> {
            if (one.isController) return -1;
            if (two.isController) return +1;
            if (one.isTile && !two.isTile) return -1;
            if (two.isTile && !one.isTile) return +1;
            if (one.blockId != two.blockId) return two.blockId - one.blockId;
            return two.amount - one.amount;
        }).map(PartInfo::getItemStack).collect(Collectors.toList());
        Map<BlockPos, TraceabilityPredicate> predicateMap = new HashMap<>();
        if (controllerBase != null) {
            controllerBase.state = new MultiblockState(world, controllerBase.getPos());
            controllerBase.checkPattern();
            controllerBase.onStructureFormed();
            // TODO
//            controllerBase.state.getCache().forEach(pos-> predicateMap.put(pos, (TraceabilityPredicate) blockInfo.getInfo()));
        }
        return new MBPattern(worldSceneRenderer, parts, predicateMap);
    }

    @SideOnly(Side.CLIENT)
    private void renderBlockOverLay(BlockPos pos, int r, int g, int b) {
        if (pos == null) return;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.translate((pos.getX() + 0.5), (pos.getY() + 0.5), (pos.getZ() + 0.5));
        GlStateManager.scale(1.01, 1.01, 1.01);

        Tessellator tessellator = Tessellator.getInstance();
        GlStateManager.disableTexture2D();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        RenderUtils.renderCubeFace(buffer, -0.5f, -0.5f, -0.5f, 0.5, 0.5, 0.5, r, g, b, 1);
        tessellator.draw();

        GlStateManager.scale(1 / 1.01, 1 / 1.01, 1 / 1.01);
        GlStateManager.translate(-(pos.getX() + 0.5), -(pos.getY() + 0.5), -(pos.getZ() + 0.5));
        GlStateManager.enableTexture2D();

        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1, 1, 1, 1);
    }

    public static ItemStack getHoveredItemStack() {
        if(lastRender > System.currentTimeMillis() - 100) {
            return tooltipBlockStack;
        }
        return null;
    }


}