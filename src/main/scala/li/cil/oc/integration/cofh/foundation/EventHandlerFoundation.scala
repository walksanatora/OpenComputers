package li.cil.oc.integration.cofh.foundation

import cofh.thermal.core.item.WrenchItem
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

object EventHandlerFoundation {
  def useWrench(player: PlayerEntity, pos: BlockPos, changeDurability: Boolean): Boolean = {
    player.getItemInHand(Hand.MAIN_HAND).getItem match {
      case wrench: WrenchItem => true
      case _ => false
    }
  }

  def isWrench(stack: ItemStack): Boolean = stack.getItem.isInstanceOf[WrenchItem]
}
