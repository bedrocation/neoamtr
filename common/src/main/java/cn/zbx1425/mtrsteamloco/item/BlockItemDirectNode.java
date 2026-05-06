package cn.zbx1425.mtrsteamloco.item;

import mtr.CreativeModeTabs;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import mtr.mappings.Text;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import cn.zbx1425.mtrsteamloco.block.BlockDirectNode.BlockEntityDirectNode;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import mtr.mappings.RegistryUtilities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Function;
import java.util.List;
// import javax.annotation.Nullable;

public class BlockItemDirectNode extends BlockItem {
    public final CreativeModeTabs.Wrapper creativeModeTab;

    public BlockItemDirectNode(CreativeModeTabs.Wrapper creativeModeTab, Block block)  {
        super(block, RegistryUtilities.createItemProperties(creativeModeTab::get));
        this.creativeModeTab = creativeModeTab;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> list, TooltipFlag flag) {
        if (stack.getItem() instanceof BlockItemDirectNode) {
            CustomData customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);

            if (customData == null) {
                list.add(Text.translatable("tooltip.mtrsteamloco.direct_node.unbound"));
                return;
            }

            CompoundTag tag = customData.copyTag();

            if (tag.contains(BlockEntityDirectNode.KEY_ANGLE)) {
                double angle = tag.getDouble(BlockEntityDirectNode.KEY_ANGLE);
                list.add(Text.translatable("tooltip.mtrsteamloco.direct_node.bound", angle));
            } else {
                list.add(Text.translatable("tooltip.mtrsteamloco.direct_node.unbound"));
            }
        }
    }
}