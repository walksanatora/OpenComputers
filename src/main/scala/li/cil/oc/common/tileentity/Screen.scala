package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network._
import li.cil.oc.client.gui
import li.cil.oc.common.component.TextBuffer
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.Color
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ArrowEntity
import net.minecraft.nbt.CompoundNBT
import net.minecraft.tileentity.TileEntity
import net.minecraft.tileentity.TileEntityType
import net.minecraft.util.Direction
import net.minecraft.util.math.AxisAlignedBB
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn

import scala.collection.mutable
import scala.language.postfixOps

class Screen(selfType: TileEntityType[_ <: Screen], var tier: Int) extends TileEntity(selfType) with traits.TextBuffer with SidedEnvironment with traits.Rotatable with traits.RedstoneAware with traits.Colored with Analyzable with Ordered[Screen] {
  def this(selfType: TileEntityType[_ <: Screen]) = this(selfType, 0)

  // Enable redstone functionality.
  _isOutputEnabled = true

  override def validFacings = Direction.values

  // ----------------------------------------------------------------------- //

  /**
    * Check for multi-block screen option in next update. We do this in the
    * update to avoid unnecessary checks on chunk unload.
    */
  var shouldCheckForMultiBlock = true

  /**
    * On the client we delay connecting screens a little, to avoid glitches
    * when not all tile entity data for a chunk has been received within a
    * single tick (meaning some screens are still "missing").
    */
  var delayUntilCheckForMultiBlock = 40

  var width, height = 1

  var origin = this

  val screens = mutable.Set(this)

  var hadRedstoneInput = false

  var cachedBounds: Option[AxisAlignedBB] = None

  var invertTouchMode = false

  private val arrows = mutable.Set.empty[ArrowEntity]

  private val lastWalked = mutable.WeakHashMap.empty[Entity, (Int, Int)]

  setColor(Color.rgbValues(Color.byTier(tier)))

  @OnlyIn(Dist.CLIENT)
  override def canConnect(side: Direction) = side != facing

  // Allow connections from front for keyboards, and keyboards only...
  override def sidedNode(side: Direction) = if (side != facing || (getLevel.isLoaded(getBlockPos.relative(side)) && getLevel.getBlockEntity(getBlockPos.relative(side)).isInstanceOf[Keyboard])) node else null

  // ----------------------------------------------------------------------- //

  def isOrigin = origin == this

  def localPosition = {
    val lpos = project(this)
    val opos = project(origin)
    (lpos.x - opos.x, lpos.y - opos.y)
  }

  def hasKeyboard = screens.exists(screen =>
    Direction.values.map(side => (side, {
      val blockPos = BlockPosition(screen).offset(side)
      if (getLevel.blockExists(blockPos)) getLevel.getBlockEntity(blockPos)
      else null
    })).exists {
      case (side, keyboard: Keyboard) => keyboard.hasNodeOnSide(side.getOpposite)
      case _ => false
    })

  def checkMultiBlock() {
    shouldCheckForMultiBlock = true
    width = 1
    height = 1
    origin = this
    screens.clear()
    screens += this
    cachedBounds = None
    invertTouchMode = false
  }

  def toScreenCoordinates(hitX: Double, hitY: Double, hitZ: Double): (Boolean, Option[(Double, Double)]) = {
    // Compute absolute position of the click on the face, measured in blocks.
    def dot(f: Direction) = f.getStepX * hitX + f.getStepY * hitY + f.getStepZ * hitZ
    val (hx, hy) = (dot(toGlobal(Direction.EAST)), dot(toGlobal(Direction.UP)))
    val tx = if (hx < 0) 1 + hx else hx
    val ty = 1 - (if (hy < 0) 1 + hy else hy)
    val (lx, ly) = localPosition
    val (ax, ay) = (lx + tx, height - 1 - ly + ty)

    // Get the relative position in the *display area* of the face.
    val border = 2.25 / 16.0
    if (ax <= border || ay <= border || ax >= width - border || ay >= height - border) {
      return (false, None)
    }
    if (!getLevel.isClientSide) return (true, None)

    val (iw, ih) = (width - border * 2, height - border * 2)
    val (rx, ry) = ((ax - border) / iw, (ay - border) / ih)

    // Make it a relative position in the displayed buffer.
    val bw = origin.buffer.getViewportWidth
    val bh = origin.buffer.getViewportHeight
    val (bpw, bph) = (origin.buffer.renderWidth / iw.toDouble, origin.buffer.renderHeight / ih.toDouble)
    val (brx, bry) = if (bpw > bph) {
      val rh = bph.toDouble / bpw.toDouble
      val bry = (ry - (1 - rh) * 0.5) / rh
      (rx, bry)
    }
    else if (bph > bpw) {
      val rw = bpw.toDouble / bph.toDouble
      val brx = (rx - (1 - rw) * 0.5) / rw
      (brx, ry)
    }
    else {
      (rx, ry)
    }

    val inBounds = bry >= 0 && bry <= 1 && brx >= 0 || brx <= 1
    (inBounds, Some((brx * bw, bry * bh)))
  }

  def copyToAnalyzer(hitX: Double, hitY: Double, hitZ: Double): Boolean = {
    val (inBounds, coordinates) = toScreenCoordinates(hitX, hitY, hitZ)
    coordinates match {
      case Some((x, y)) => origin.buffer match {
        case buffer: TextBuffer =>
          buffer.copyToAnalyzer(y.toInt, null)
          true
        case _ => false
      }
      case _ => inBounds
    }
  }

  def click(hitX: Double, hitY: Double, hitZ: Double): Boolean = {
    val (inBounds, coordinates) = toScreenCoordinates(hitX, hitY, hitZ)
    coordinates match {
      case Some((x, y)) =>
        // Send the packet to the server (manually, for accuracy).
        origin.buffer.mouseDown(x, y, 0, null)
        true
      case _ => inBounds
    }
  }

  def walk(entity: Entity) {
    val (x, y) = localPosition
    origin.lastWalked.put(entity, localPosition) match {
      case Some((oldX, oldY)) if oldX == x && oldY == y => // Ignore
      case _ => entity match {
        case player: PlayerEntity if Settings.get.inputUsername =>
          origin.node.sendToReachable("computer.signal", "walk", Int.box(x + 1), Int.box(height - y), player.getName.getString)
        case _ =>
          origin.node.sendToReachable("computer.signal", "walk", Int.box(x + 1), Int.box(height - y))
      }
    }
  }

  def shot(arrow: ArrowEntity) {
    arrows.add(arrow)
  }

  // ----------------------------------------------------------------------- //

  override def updateEntity() {
    super.updateEntity()
    if (shouldCheckForMultiBlock && ((isClient && isClientReadyForMultiBlockCheck) || (isServer && isConnected))) {
      // Make sure we merge in a deterministic order, to avoid getting
      // different results on server and client due to the update order
      // differing between the two. This also saves us from having to save
      // any multi-block specific state information.
      val pending = mutable.SortedSet(this)
      val queue = mutable.Queue(this)
      while (queue.nonEmpty) {
        val current = queue.dequeue()
        val lpos = project(current)
        def tryQueue(dx: Int, dy: Int) {
          val npos = unproject(lpos.x + dx, lpos.y + dy, lpos.z)
          if (getLevel.blockExists(npos)) getLevel.getBlockEntity(npos) match {
            case s: Screen if s.pitch == pitch && s.yaw == yaw && pending.add(s) => queue += s
            case _ => // Ignore.
          }
        }
        tryQueue(-1, 0)
        tryQueue(1, 0)
        tryQueue(0, -1)
        tryQueue(0, 1)
      }
      // Perform actual merges.
      while (pending.nonEmpty) {
        val current = pending.firstKey
        while (current.tryMerge()) {}
        current.screens.foreach {
          screen =>
            screen.shouldCheckForMultiBlock = false
            pending.remove(screen)
            queue += screen
        }
      }
      if (isClient) updateMergedModels()
      // Update visibility after everything is done, to avoid noise.
      queue.foreach(screen => {
        val buffer = screen.buffer
        if (screen.isOrigin) {
          if (isServer) {
            buffer.node.asInstanceOf[Component].setVisibility(Visibility.Network)
            buffer.setEnergyCostPerTick(Settings.get.screenCost * screen.width * screen.height)
            buffer.setAspectRatio(screen.width, screen.height)
          }
        }
        else {
          if (isServer) {
            buffer.node.asInstanceOf[Component].setVisibility(Visibility.None)
            buffer.setEnergyCostPerTick(Settings.get.screenCost)
          }
          buffer.setAspectRatio(1, 1)
          val w = buffer.getWidth
          val h = buffer.getHeight
          buffer.setForegroundColor(0xFFFFFF, false)
          buffer.setBackgroundColor(0x000000, false)
          buffer.fill(0, 0, w, h, ' ')
        }
      })
    }
    if (arrows.nonEmpty) {
      for (arrow <- arrows) {
        val hitX = arrow.getX - x
        val hitY = arrow.getY - y
        val hitZ = arrow.getZ - z
        arrow.getOwner match {
          case player: PlayerEntity if player == Minecraft.getInstance.player => click(hitX, hitY, hitZ)
          case _ =>
        }
      }
      arrows.clear()
    }
  }

  private def updateMergedModels() {
    if (getLevel == Minecraft.getInstance.level) {
      val renderer = Minecraft.getInstance.levelRenderer
      screens.foreach(screen => {
        val pos = screen.getBlockPos
        renderer.setSectionDirty(pos.getX >> 4, pos.getY >> 4, pos.getZ >> 4)
      })
    }
  }

  private def isClientReadyForMultiBlockCheck = if (delayUntilCheckForMultiBlock > 0) {
    delayUntilCheckForMultiBlock -= 1
    false
  } else true

  override def dispose() {
    super.dispose()
    screens.clone().foreach(_.checkMultiBlock())
    if (isClient) {
      Minecraft.getInstance.screen match {
        case screenGui: gui.Screen if screenGui.buffer == buffer => screenGui.onClose()
        case _ =>
      }
    }
  }

  override protected def onColorChanged() {
    super.onColorChanged()
    screens.clone().foreach(_.checkMultiBlock())
  }

  // ----------------------------------------------------------------------- //

  private final val TierTag = Settings.namespace + "tier"
  private final val HadRedstoneInputTag = Settings.namespace + "hadRedstoneInput"
  private final val InvertTouchModeTag = Settings.namespace + "invertTouchMode"

  override def loadForServer(nbt: CompoundNBT) {
    tier = nbt.getByte(TierTag) max 0 min 2
    setColor(Color.rgbValues(Color.byTier(tier)))
    super.loadForServer(nbt)
    hadRedstoneInput = nbt.getBoolean(HadRedstoneInputTag)
    invertTouchMode = nbt.getBoolean(InvertTouchModeTag)
  }

  override def saveForServer(nbt: CompoundNBT) {
    nbt.putByte(TierTag, tier.toByte)
    super.saveForServer(nbt)
    nbt.putBoolean(HadRedstoneInputTag, hadRedstoneInput)
    nbt.putBoolean(InvertTouchModeTag, invertTouchMode)
  }

  @OnlyIn(Dist.CLIENT) override
  def loadForClient(nbt: CompoundNBT) {
    tier = nbt.getByte(TierTag) max 0 min 2
    super.loadForClient(nbt)
    invertTouchMode = nbt.getBoolean(InvertTouchModeTag)
  }

  override def saveForClient(nbt: CompoundNBT) {
    nbt.putByte(TierTag, tier.toByte)
    super.saveForClient(nbt)
    nbt.putBoolean(InvertTouchModeTag, invertTouchMode)
  }

  // ----------------------------------------------------------------------- //

  @OnlyIn(Dist.CLIENT)
  override def getRenderBoundingBox =
    if ((width == 1 && height == 1) || !isOrigin) super.getRenderBoundingBox
    else cachedBounds match {
      case Some(bounds) => bounds
      case _ =>
        val spos = unproject(width, height, 1)
        val ox = x + (if (spos.x < 0) 1 else 0)
        val oy = y + (if (spos.y < 0) 1 else 0)
        val oz = z + (if (spos.z < 0) 1 else 0)
        val btmp = new AxisAlignedBB(ox, oy, oz, ox + spos.x, oy + spos.y, oz + spos.z)
        val b = new AxisAlignedBB(
          math.min(btmp.minX, btmp.maxX), math.min(btmp.minY, btmp.maxY), math.min(btmp.minZ, btmp.maxZ),
          math.max(btmp.minX, btmp.maxX), math.max(btmp.minY, btmp.maxY), math.max(btmp.minZ, btmp.maxZ))
        cachedBounds = Some(b)
        b
    }

  @OnlyIn(Dist.CLIENT)
  override def getViewDistance = if (isOrigin) super.getViewDistance else 0

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: PlayerEntity, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = Array(origin.node)

  override protected def onRedstoneInputChanged(args: RedstoneChangedEventArgs) {
    super.onRedstoneInputChanged(args)
    val hasRedstoneInput = screens.map(_.maxInput).max > 0
    if (hasRedstoneInput != hadRedstoneInput) {
      hadRedstoneInput = hasRedstoneInput
      if (hasRedstoneInput) {
        origin.buffer.setPowerState(!origin.buffer.getPowerState)
      }
    }
  }

  override def onRotationChanged() {
    super.onRotationChanged()
    screens.clone().foreach(_.checkMultiBlock())
  }

  // ----------------------------------------------------------------------- //

  override def compare(that: Screen) =
    if (x != that.x) x - that.x
    else if (y != that.y) y - that.y
    else z - that.z

  // ----------------------------------------------------------------------- //

  private def tryMerge() = {
    val opos = project(origin)
    def tryMergeTowards(dx: Int, dy: Int) = {
      val npos = unproject(opos.x + dx, opos.y + dy, opos.z)
      getLevel.blockExists(npos) && (getLevel.getBlockEntity(npos) match {
        case s: Screen if s.tier == tier && s.pitch == pitch && s.getColor == getColor && s.yaw == yaw && !screens.contains(s) =>
          val spos = project(s.origin)
          val canMergeAlongX = spos.y == opos.y && s.height == height && s.width + width <= Settings.get.maxScreenWidth
          val canMergeAlongY = spos.x == opos.x && s.width == width && s.height + height <= Settings.get.maxScreenHeight
          if (canMergeAlongX || canMergeAlongY) {
            val (newOrigin) =
              if (canMergeAlongX) {
                if (spos.x < opos.x) s.origin else origin
              }
              else {
                if (spos.y < opos.y) s.origin else origin
              }
            val (newWidth, newHeight) =
              if (canMergeAlongX) (width + s.width, height)
              else (width, height + s.height)
            val newScreens = screens ++ s.screens
            for (screen <- newScreens) {
              screen.width = newWidth
              screen.height = newHeight
              screen.origin = newOrigin
              screen.screens ++= newScreens // It's a set, so there won't be duplicates.
              screen.cachedBounds = None
            }
            true
          }
          else false // Cannot merge.
        case _ => false
      })
    }
    tryMergeTowards(0, height) || tryMergeTowards(0, -1) || tryMergeTowards(width, 0) || tryMergeTowards(-1, 0)
  }

  private def project(t: Screen) = {
    def dot(f: Direction, s: Screen) = f.getStepX * s.x + f.getStepY * s.y + f.getStepZ * s.z
    BlockPosition(dot(toGlobal(Direction.EAST), t), dot(toGlobal(Direction.UP), t), dot(toGlobal(Direction.SOUTH), t))
  }

  private def unproject(x: Int, y: Int, z: Int) = {
    def dot(f: Direction) = f.getStepX * x + f.getStepY * y + f.getStepZ * z
    BlockPosition(dot(toLocal(Direction.EAST)), dot(toLocal(Direction.UP)), dot(toLocal(Direction.SOUTH)))
  }
}
