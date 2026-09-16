package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.mod.math.Matrix4;
import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.Config.ConfigBalance;
import cam72cam.immersiverailroading.inventory.FilteredStackHandler;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.model.FreightModel;
import cam72cam.immersiverailroading.registry.FreightDefinition;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.ItemEntity;
import cam72cam.mod.entity.Living;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.serialization.TagField;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.Set;

public abstract class Freight extends EntityCoupleableRollingStock {
	@TagField("items")
	public FilteredStackHandler cargoItems = new FilteredStackHandler(0);

	@TagSync
	@TagField("CARGO_ITEMS")
	private int itemCount = 0;

	@TagSync
	@TagField("PERCENT_FULL")
	private int percentFull = 0;

	@TagField("flanVehicle")
	public FlanVehicleCargo flanVehicleCargo = new FlanVehicleCargo();

	public abstract int getInventorySize();
	public abstract int getInventoryWidth();

	@Override
	public FreightDefinition getDefinition() {
		return this.getDefinition(FreightDefinition.class);
	}

	public boolean hasFlanVehicle() {
		return flanVehicleCargo != null && flanVehicleCargo.hasVehicle();
	}

	public FlanVehicleCargo getFlanVehicleCargo() {
		return flanVehicleCargo;
	}

	public void setFlanVehicleCargo(FlanVehicleCargo cargo) {
		this.flanVehicleCargo = cargo;
	}

	public void clearFlanVehicleCargo() {
		if (flanVehicleCargo != null) {
			flanVehicleCargo.clear();
		}
	}

	/*
	 * 
	 * EntityRollingStock Overrides
	 */

	@Override
	public void onAssemble() {
		super.onAssemble();
		List<ItemStack> extras = cargoItems.setSize(this.getInventorySize());
		if (getWorld().isServer) {
			extras.forEach(stack -> getWorld().dropItem(stack, getPosition()));
			cargoItems.onChanged(slot -> handleMass());
			handleMass();
		}
		initContainerFilter();
	}
	
	@Override
	public void onDissassemble() {
		super.onDissassemble();

		if (getWorld().isServer) {
			for (int i = 0; i < cargoItems.getSlotCount(); i++) {
				ItemStack stack = cargoItems.get(i);
				if (!stack.isEmpty()) {
					getWorld().dropItem(stack.copy(), getPosition());
					stack.setCount(0);
					cargoItems.set(i, stack);
				}
			}
		}
	}

	@Override
	public ClickResult onClick(Player player, Player.Hand hand) {
		ClickResult clickRes = super.onClick(player, hand);
		if (clickRes != ClickResult.PASS) {
			return clickRes;
		}

		if (!this.isBuilt()) {
			return ClickResult.PASS;
		}

		// Check for Flan vehicle undocking - player tries to enter docked vehicle
		if (Config.ConfigFlanVehicle.autoUndockOnEnter && hasFlanVehicle() && player.getHeldItem(hand).isEmpty() && getWorld().isServer) {
			// Check if player is near the cargo anchor position (where vehicle is rendered)
			Vec3d cargoAnchor = getCargoAnchorPosition();
			if (cargoAnchor != null && player.getPosition().distanceTo(cargoAnchor) < 3.0) {
				if (tryUndockFlanVehicle(player)) {
					return ClickResult.ACCEPTED;
				}
			}
		}

		// See ItemLead.attachToFence
		if (this.getDefinition().acceptsLivestock()) {
			List<Living> leashed = getWorld().getEntities((Living e) -> e.getPosition().distanceTo(player.getPosition()) < 16 && e.isLeashedTo(player), Living.class);
			if (getWorld().isClient && !leashed.isEmpty()) {
				return ClickResult.ACCEPTED;
			}
			if (player.hasPermission(Permissions.BOARD_WITH_LEAD)) {
				for (Living entity : leashed) {
					if (canFitPassenger(entity)) {
						entity.unleash(player);
						this.addPassenger(entity);
						return ClickResult.ACCEPTED;
					}
				}
			}

			if (player.getHeldItem(hand).is(Fuzzy.LEAD)) {
				for (Entity passenger : this.getPassengers()) {
					if (passenger instanceof Living && !passenger.isVillager()) {
						if (getWorld().isServer) {
							Living living = (Living) passenger;
							if (living.canBeLeashedTo(player)) {
								this.removePassenger(living);
								living.setLeashHolder(player);
								player.getHeldItem(hand).shrink(1);
							}
						}
						return ClickResult.ACCEPTED;
					}
				}
			}
		}

		if (player.getHeldItem(hand).isEmpty() || player.getRiding() != this) {
			if (getWorld().isClient || openGui(player)) {
				return ClickResult.ACCEPTED;
			}
		}
		return ClickResult.PASS;
	}

	protected boolean tryUndockFlanVehicle(Player player) {
		if (!hasFlanVehicle()) {
			return false;
		}

		if (!Config.ConfigFlanVehicle.allowDockWhileMoving && !getCurrentSpeed().isZero()) {
			player.sendMessage(cam72cam.mod.text.PlayerMessage.direct("Cannot undock: train is moving"));
			return false;
		}

		Vec3d spawnPos = findSafeUndockPosition(player);
		if (spawnPos == null) {
			player.sendMessage(cam72cam.mod.text.PlayerMessage.direct("Cannot undock: insufficient clearance"));
			return false;
		}

		FlanVehicleCargo cargo = flanVehicleCargo;
		if (!cam72cam.immersiverailroading.thirdparty.FlanVehicleHelper.isFlanLoaded()) {
			player.sendMessage(cam72cam.mod.text.PlayerMessage.direct("Cannot undock: Flan's Mod not installed"));
			return false;
		}

		cam72cam.immersiverailroading.thirdparty.FlanVehicleHelper.spawnVehicle(this, cargo, player);

		clearFlanVehicleCargo();
		
		return true;
	}

	private Vec3d findSafeUndockPosition(Player player) {
		Vec3d flatcarPos = getPosition();
		Vec3d playerPos = player.getPosition();
		
		double dx = playerPos.x - flatcarPos.x;
		double dz = playerPos.z - flatcarPos.z;
		
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < 0.1) {
			dist = 1;
		}
		
		double offset = 3.0;
		double spawnX = flatcarPos.x + (dx / dist) * offset;
		double spawnZ = flatcarPos.z + (dz / dist) * offset;
		double spawnY = flatcarPos.y + 1.5;
		
		if (isPositionClear(spawnX, spawnY, spawnZ)) {
			return new Vec3d(spawnX, spawnY, spawnZ);
		}
		
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4;
			double testX = flatcarPos.x + Math.cos(angle) * offset;
			double testZ = flatcarPos.z + Math.sin(angle) * offset;
			if (isPositionClear(testX, spawnY, testZ)) {
				return new Vec3d(testX, spawnY, testZ);
			}
		}
		
		return null;
	}

	private boolean isPositionClear(double x, double y, double z) {
		if (!Config.ConfigFlanVehicle.checkClearance) {
			return true;
		}
		
		net.minecraft.util.AxisAlignedBB box = net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 1.5, y, z - 1.5, x + 1.5, y + 3.0, z + 1.5);
		List<net.minecraft.entity.Entity> entities = getWorld().getEntitiesWithinAABBExcludingEntity(null, box);
		for (net.minecraft.entity.Entity entity : entities) {
			if (entity != this && !entity.isDead && entity.canBeCollidedWith()) {
				return false;
			}
		}
		
		for (int bx = (int)Math.floor(x - 1.5); bx <= (int)Math.floor(x + 1.5); bx++) {
			for (int by = (int)Math.floor(y); by <= (int)Math.floor(y + 2.5); by++) {
				for (int bz = (int)Math.floor(z - 1.5); bz <= (int)Math.floor(z + 1.5); bz++) {
					net.minecraft.block.Block block = getWorld().getBlock(bx, by, bz);
					if (block != null && block.getMaterial().isSolid()) {
						return false;
					}
				}
			}
		}
		
		return true;
	}

	private Vec3d getCargoAnchorPosition() {
		FreightModel<?, ?> model = (FreightModel<?, ?>) this.getDefinition().getModel();
		if (model != null && model.flanVehicleRenderer != null && !model.flanVehicleRenderer.getComponents().isEmpty()) {
			ModelComponent comp = model.flanVehicleRenderer.getComponents().get(0);
			Matrix4 matrix = getModelMatrix();
			Vec3d localPos = new Vec3d(comp.center.x, comp.min.y, comp.center.z);
			return matrix.apply(localPos);
		}
		return getPosition().add(0, 1.5, 0);
	}

	protected boolean openGui(Player player) {
		if (getInventorySize() == 0) {
			return false;
		}
		if (player.hasPermission(Permissions.FREIGHT_INVENTORY)) {
			GuiTypes.FREIGHT.open(player, this);
		}
		return true;
	}

    @Override
    public void onTick() {
        super.onTick();

        if (this.getWorld().isClient || !Config.ImmersionConfig.allowCargoLoadDroppedItem || this.getWorld().getTicks() % 5 != 0) {
            return;
        }

        int tankOffset;
        int inventorySize = this.getInventorySize();
        if (this instanceof FreightTank) {
            if (inventorySize == 2) {
                return;
            } else {
                tankOffset = 2;
            }
        } else {
            tankOffset = 0;
        }

        Set<ItemEntity> nearby = this.getNearbyItems();
        nearby.forEach(entity -> {
                  ItemStack stack = entity.getContent();
                  ItemStack remaining = stack;
                  for (int i = tankOffset; i < inventorySize; i++) {
                      if ((remaining = cargoItems.insert(i, remaining, false)).getCount() == 0) {
                          entity.kill();
                          return;
                      }
                  }
                  if(stack != remaining){
                      entity.setContent(remaining);
                  }
              });
    }

    public Set<ItemEntity> getNearbyItems() {
        IBoundingBox grow = this.getBounds();
        List<ItemEntity> itemEntities = getWorld().getEntitiesWithinBB(grow, ItemEntity.class);
        FreightModel<?, ?> model = (FreightModel<?, ?>) this.getDefinition().getModel();
        return model.filterItems(this, itemEntities);
    }

	protected void handleMass() {
		int itemInsideCount = 0;
		int stacksWithStuff = 0;
		for (int slot = 0; slot < cargoItems.getSlotCount(); slot++) {
			itemInsideCount += cargoItems.get(slot).getCount();
			if (cargoItems.get(slot).getCount() != 0) {
				stacksWithStuff += 1;
			}
		}
		itemCount = itemInsideCount;
		percentFull = this.getInventorySize() > 0 ? stacksWithStuff * 100 / this.getInventorySize() : 100;
	}
	
	public int getPercentCargoFull() {
		return percentFull;
	}

	protected void initContainerFilter() {
		
	}

	@Override
	public double getWeight() {
		double fLoad = ConfigBalance.blockWeight * itemCount;
		fLoad = fLoad + super.getWeight();
		return fLoad;
	}

	@Override
	public double getMaxWeight() {
		return super.getMaxWeight() + ConfigBalance.blockWeight * getInventorySize() * 64;
	}
}
