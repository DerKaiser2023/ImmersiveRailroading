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
import com.flansmod.common.driveables.DriveableData;
import com.flansmod.common.driveables.DriveableType;
import com.flansmod.common.driveables.EnumDriveablePart;
import com.flansmod.common.driveables.EntityVehicle;
import com.flansmod.common.driveables.VehicleType;
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

		// Check if flatcar is stationary
		if (!Config.ConfigFlanVehicle.allowDockWhileMoving && !getCurrentSpeed().isZero()) {
			player.sendMessage(cam72cam.mod.text.PlayerMessage.direct("Cannot undock: train is moving"));
			return false;
		}

		// Find safe spawn position
		Vec3d spawnPos = findSafeUndockPosition(player);
		if (spawnPos == null) {
			player.sendMessage(cam72cam.mod.text.PlayerMessage.direct("Cannot undock: insufficient clearance"));
			return false;
		}

		// Restore the Flan vehicle
		FlanVehicleCargo cargo = flanVehicleCargo;
		VehicleType type = VehicleType.getVehicle(cargo.vehicleType);
		if (type == null) {
			player.sendMessage(cam72cam.mod.text.PlayerMessage.direct("Cannot undock: vehicle type not found"));
			return false;
		}

		// Create the vehicle entity
		EntityVehicle vehicle = new EntityVehicle(getWorld(), spawnPos.x, spawnPos.y, spawnPos.z, cargo.rotationYaw, type, restoreDriveableData(cargo));
		vehicle.setOwner(cargo.ownerName != null ? com.hfr.faction.Factions.getFactionFromName(cargo.ownerName) : null);
		vehicle.locked = cargo.locked;
		vehicle.stolen = cargo.stolen;
		
		// Restore parts health
		for (int i = 0; i < EnumDriveablePart.values().length; i++) {
			EnumDriveablePart part = EnumDriveablePart.values()[i];
			DriveablePart driveablePart = vehicle.getDriveableData().parts.get(part);
			if (driveablePart != null && i < cargo.partsHealth.length) {
				driveablePart.health = cargo.partsHealth[i];
				driveablePart.maxHealth = cargo.partsMaxHealth[i];
				driveablePart.onFire = cargo.partsOnFire[i];
				driveablePart.crew = cargo.partsCrew[i];
			}
		}

		getWorld().spawnEntityInWorld(vehicle);
		
		// Place player in driver's seat
		player.mountEntity(vehicle.seats[0]);
		
		// Clear cargo
		clearFlanVehicleCargo();
		
		return true;
	}

	private Vec3d findSafeUndockPosition(Player player) {
		// Try to spawn on the side of the flatcar where the player is
		Vec3d flatcarPos = getPosition();
		Vec3d playerPos = player.getPosition();
		
		// Calculate direction from flatcar to player
		double dx = playerPos.x - flatcarPos.x;
		double dz = playerPos.z - flatcarPos.z;
		
		// Normalize and offset by vehicle size
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < 0.1) {
			dist = 1;
		}
		
		double offset = 3.0; // Distance from flatcar center
		double spawnX = flatcarPos.x + (dx / dist) * offset;
		double spawnZ = flatcarPos.z + (dz / dist) * offset;
		double spawnY = flatcarPos.y + 1.5; // Above flatcar deck
		
		// Check if position is clear
		if (isPositionClear(spawnX, spawnY, spawnZ)) {
			return new Vec3d(spawnX, spawnY, spawnZ);
		}
		
		// Try other positions around the flatcar
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
		// If clearance checking is disabled, always return true
		if (!Config.ConfigFlanVehicle.checkClearance) {
			return true;
		}
		
		// Check for collisions with blocks and entities
		net.minecraft.util.AxisAlignedBB box = net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 1.5, y, z - 1.5, x + 1.5, y + 3.0, z + 1.5);
		List<net.minecraft.entity.Entity> entities = getWorld().getEntitiesWithinAABBExcludingEntity(null, box);
		for (net.minecraft.entity.Entity entity : entities) {
			if (entity != this && !entity.isDead && entity.canBeCollidedWith()) {
				return false;
			}
		}
		
		// Check for solid blocks
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
		// Get the cargo anchor from the model
		FreightModel<?, ?> model = (FreightModel<?, ?>) this.getDefinition().getModel();
		if (model != null && model.flanVehicleRenderer != null && !model.flanVehicleRenderer.getComponents().isEmpty()) {
			ModelComponent comp = model.flanVehicleRenderer.getComponents().get(0);
			Matrix4 matrix = getModelMatrix();
			Vec3d localPos = new Vec3d(comp.center.x, comp.min.y, comp.center.z);
			return matrix.apply(localPos);
		}
		return getPosition().add(0, 1.5, 0); // Fallback
	}

	private DriveableData restoreDriveableData(FlanVehicleCargo cargo) {
		DriveableData data = new DriveableData(new NBTTagCompound());
		data.paintjobID = cargo.paintjobID;
		data.fuelInTank = cargo.fuelInTank;
		
		if (cargo.fuel != null) {
			data.fuel = net.minecraft.item.ItemStack.loadItemStackFromNBT(cargo.fuel);
		}
		
		// Restore inventories
		if (cargo.ammo != null) {
			data.ammo = new net.minecraft.item.ItemStack[cargo.ammo.length];
			for (int i = 0; i < cargo.ammo.length; i++) {
				if (cargo.ammo[i] != null) {
					data.ammo[i] = net.minecraft.item.ItemStack.loadItemStackFromNBT(cargo.ammo[i]);
				}
			}
		}
		
		if (cargo.bombs != null) {
			data.bombs = new net.minecraft.item.ItemStack[cargo.bombs.length];
			for (int i = 0; i < cargo.bombs.length; i++) {
				if (cargo.bombs[i] != null) {
					data.bombs[i] = net.minecraft.item.ItemStack.loadItemStackFromNBT(cargo.bombs[i]);
				}
			}
		}
		
		if (cargo.missiles != null) {
			data.missiles = new net.minecraft.item.ItemStack[cargo.missiles.length];
			for (int i = 0; i < cargo.missiles.length; i++) {
				if (cargo.missiles[i] != null) {
					data.missiles[i] = net.minecraft.item.ItemStack.loadItemStackFromNBT(cargo.missiles[i]);
				}
			}
		}
		
		if (cargo.cargo != null) {
			data.cargo = new net.minecraft.item.ItemStack[cargo.cargo.length];
			for (int i = 0; i < cargo.cargo.length; i++) {
				if (cargo.cargo[i] != null) {
					data.cargo[i] = net.minecraft.item.ItemStack.loadItemStackFromNBT(cargo.cargo[i]);
				}
			}
		}
		
		data.seatBelt = cargo.seatBelt;
		data.emergencyMode = cargo.emergencyMode;
		data.WarpLimit = cargo.warpLimit;
		
		return data;
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
                          //All items are gone, goodbye
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
        //Bypass RealBB's grow
        IBoundingBox grow = this.getBounds();
        List<ItemEntity> itemEntities = getWorld().getEntitiesWithinBB(grow, ItemEntity.class);
        FreightModel<?, ?> model = (FreightModel<?, ?>) this.getDefinition().getModel();
        return model.filterItems(this, itemEntities);
    }

	/**
	 * Handle mass depending on item count
	 */
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