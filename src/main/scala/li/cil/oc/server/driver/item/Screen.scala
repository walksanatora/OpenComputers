package li.cil.oc.server.driver.item

import li.cil.oc.api
import li.cil.oc.api.driver.Host
import li.cil.oc.common.{Slot, component, tileentity}
import net.minecraft.item.ItemStack

object Screen extends Item {
  override def worksWith(stack: ItemStack) = isOneOf(stack, api.Items.get("screen1"))

  override def createEnvironment(stack: ItemStack, host: Host) = host match {
    case screen: tileentity.Screen if screen.tier > 0 => new component.Screen(screen)
    case _ => new component.TextBuffer(host)
  }

  override def slot(stack: ItemStack) = Slot.Upgrade
}
