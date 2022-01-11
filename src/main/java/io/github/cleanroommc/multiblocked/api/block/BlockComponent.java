package io.github.cleanroommc.multiblocked.api.block;

import io.github.cleanroommc.multiblocked.Multiblocked;
import io.github.cleanroommc.multiblocked.api.tile.ComponentTileEntity;
import io.github.cleanroommc.multiblocked.client.model.IModelSupplier;
import io.github.cleanroommc.multiblocked.client.model.SimpleStateMapper;
import io.github.cleanroommc.multiblocked.util.Utils;
import io.github.cleanroommc.multiblocked.util.Vector3;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("deprecation")
public class BlockComponent extends Block implements IModelSupplier, ITileEntityProvider {
    public static final ModelResourceLocation MODEL_LOCATION = new ModelResourceLocation(new ResourceLocation(Multiblocked.MODID, "component_block"), "normal");
    public static final PropertyBool OPAQUE = PropertyBool.create("opaque");
    public static final ComponentProperty COMPONENT_PROPERTY = new ComponentProperty();
    public final ComponentTileEntity component;

    public BlockComponent(ComponentTileEntity component) {
        super(Material.IRON);
        setCreativeTab(Multiblocked.CREATIVE_TAB);
        setSoundType(SoundType.METAL);
        setHardness(1.5F);
        setResistance(10.0F);
        setTranslationKey("component_block");
        setDefaultState(getDefaultState().withProperty(OPAQUE, true));
        this.component = component;
    }

    @Override
    public boolean causesSuffocation(IBlockState state) {
        return state.getValue(OPAQUE);
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer.Builder(this).add(OPAQUE).add(COMPONENT_PROPERTY).build();
    }

    @Nonnull
    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(OPAQUE, meta % 2 == 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(OPAQUE) ? 0 : 1;
    }

    @Override
    public boolean canCreatureSpawn(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull EntityLiving.SpawnPlacementType type) {
        return false;
    }

    public static ComponentTileEntity getComponent(IBlockAccess blockAccess, BlockPos pos) {
        TileEntity instance = blockAccess.getTileEntity(pos);
        return instance instanceof ComponentTileEntity ? ((ComponentTileEntity) instance) : null;
    }

    @Override
    public boolean doesSideBlockRendering(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull EnumFacing face) {
        return state.isOpaqueCube() && getComponent(world, pos) != null;
    }

    @Nonnull
    @Override
    public ItemStack getPickBlock(@Nonnull IBlockState state, @Nonnull RayTraceResult target, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull EntityPlayer player) {
        ComponentTileEntity instance = getComponent(world, pos);
        return instance == null ? ItemStack.EMPTY : instance.getStackForm();
    }

    @Override
    public void addCollisionBoxToList(@Nonnull IBlockState state, @Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull AxisAlignedBB entityBox, @Nonnull List<AxisAlignedBB> collidingBoxes, @Nullable Entity entityIn, boolean isActualState) {
        ComponentTileEntity instance = getComponent(worldIn, pos);
        if (instance != null) {
            for (AxisAlignedBB boundingBox : instance.getCollisionBoundingBox()) {
                AxisAlignedBB offset = boundingBox.offset(pos);
                if (offset.intersects(entityBox)) collidingBoxes.add(offset);
            }
        }
    }

    @Nullable
    @Override
    public RayTraceResult collisionRayTrace(@Nonnull IBlockState blockState, @Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull Vec3d start, @Nonnull Vec3d end) {
        ComponentTileEntity instance = getComponent(worldIn, pos);
        if (instance != null) {
            return Utils.rayTraceClosest(pos, new Vector3(start), new Vector3(end), instance.getCollisionBoundingBox());
        }
        return this.rayTrace(pos, start, end, blockState.getBoundingBox(worldIn, pos));
    }

    @Override
    public boolean rotateBlock(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull EnumFacing axis) {
        ComponentTileEntity instance = getComponent(world, pos);
        return instance != null && instance.setFrontFacing(axis);
    }

    @Nullable
    @Override
    public EnumFacing[] getValidRotations(@Nonnull World world, @Nonnull BlockPos pos) {
        ComponentTileEntity instance = getComponent(world, pos);
        return instance == null ? null : Arrays.stream(EnumFacing.VALUES)
                .filter(instance::isValidFrontFacing)
                .toArray(EnumFacing[]::new);
    }

    @Override
    public void getDrops(@Nonnull NonNullList<ItemStack> drops, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull IBlockState state, int fortune) {
        ComponentTileEntity instance = getComponent(world, pos);
        if (instance == null) return;
        instance.getDrops(drops, harvesters.get());
    }

    @Override
    public boolean onBlockActivated(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer playerIn, @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        ComponentTileEntity instance = getComponent(worldIn, pos);
        return instance != null && instance.onRightClick(playerIn, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public void onBlockClicked(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull EntityPlayer playerIn) {
        ComponentTileEntity instance = getComponent(worldIn, pos);
        if (instance != null) {
            instance.onLeftClick(playerIn);
        }
    }

    @Override
    public boolean canConnectRedstone(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nullable EnumFacing side) {
        ComponentTileEntity instance = getComponent(world, pos);
        return instance != null && instance.canConnectRedstone(side == null ? null : side.getOpposite());
    }

    @Override
    public boolean shouldCheckWeakPower(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull EnumFacing side) {
        return false;
    }

    @Override
    public int getWeakPower(@Nonnull IBlockState blockState, @Nonnull IBlockAccess blockAccess, @Nonnull BlockPos pos, @Nonnull EnumFacing side) {
        ComponentTileEntity instance = getComponent(blockAccess, pos);
        return instance == null ? 0 : instance.getOutputRedstoneSignal(side.getOpposite());
    }

    @Override
    public void neighborChanged(@Nonnull IBlockState state, @Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull Block blockIn, @Nonnull BlockPos fromPos) {
        ComponentTileEntity instance = getComponent(worldIn, pos);
        if (instance != null) {
            instance.onNeighborChanged();
        }
    }

    @Override
    public boolean canRenderInLayer(@Nonnull IBlockState state, @Nonnull BlockRenderLayer layer) {
        return true;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return state.getValue(OPAQUE);
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return state.getValue(OPAQUE);
    }

    @Override
    public int getLightOpacity(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        return state.getValue(OPAQUE) ? 255 : 1;
    }

    @Override
    public void getSubBlocks(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        items.add(component.getStackForm());
    }

    @Override
    public boolean canEntityDestroy(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull Entity entity) {
        return !(entity instanceof EntityWither || entity instanceof EntityWitherSkull);
    }

    @Override
    @Nonnull
    public IBlockState getExtendedState(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        return ((IExtendedBlockState) state).withProperty(COMPONENT_PROPERTY, getComponent(world, pos));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        component.registerRenderers(event.getMap());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onModelRegister() {
        ModelLoader.setCustomStateMapper(this, new SimpleStateMapper(MODEL_LOCATION));
        for (IBlockState state : this.getBlockState().getValidStates()) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(this), this.getMetaFromState(state), MODEL_LOCATION);
        }
    }

    @Override
    public TileEntity createNewTileEntity(@Nonnull World worldIn, int meta) {
        return component.createNewTileEntity();
    }

    private static class ComponentProperty implements IUnlistedProperty<ComponentTileEntity> {
        @Override
        public String getName() {
            return "component";
        }

        @Override
        public boolean isValid(ComponentTileEntity controller) {
            return true;
        }

        @Override
        public Class<ComponentTileEntity> getType() {
            return ComponentTileEntity.class;
        }

        @Override
        public String valueToString(ComponentTileEntity controller) {
            return controller == null ? "null" : controller.toString();
        }
    }
}
