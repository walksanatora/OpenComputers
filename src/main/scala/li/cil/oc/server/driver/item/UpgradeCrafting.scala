package li.cil.oc.server.driver.item

import li.cil.oc.api
import li.cil.oc.api.driver.Host
import li.cil.oc.api.tileentity.Robot
import li.cil.oc.common.{Slot, Tier}
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object UpgradeCrafting extends Item {
  override def worksWith(stack: ItemStack) = isOneOf(stack, api.Items.get("craftingUpgrade"))

  override def createEnvironment(stack: ItemStack, host: Host) =
    host match {
      case robot: Host with Robot => new component.UpgradeCrafting(robot)
      case _ => null
    }

  override def slot(stack: ItemStack) = Slot.Upgrade

  override def tier(stack: ItemStack) = Tier.Two
}
