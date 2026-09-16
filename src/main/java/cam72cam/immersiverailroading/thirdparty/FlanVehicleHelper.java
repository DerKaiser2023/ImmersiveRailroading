package cam72cam.immersiverailroading.thirdparty;

import cam72cam.immersiverailroading.entity.FlanVehicleCargo;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.math.Vec3d;
import net.minecraft.nbt.NBTTagCompound;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class FlanVehicleHelper {
    public static boolean isFlanLoaded() {
        try {
            Class.forName("com.flansmod.common.Flan");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isFlanVehicleRendererAvailable() {
        try {
            Class.forName("com.flansmod.client.model.ModelVehicle");
            Class.forName("com.flansmod.client.tmt.ModelRendererTurbo");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void spawnVehicle(Entity entity, FlanVehicleCargo cargo, Player player) {
        if (!isFlanLoaded()) return;
        try {
            Class<?> vehicleTypeClass = Class.forName("com.flansmod.common.driveables.VehicleType");
            Method getVehicle = vehicleTypeClass.getMethod("getVehicle", String.class);
            Object type = getVehicle.invoke(null, cargo.vehicleType);
            if (type == null) return;

            net.minecraft.world.World mcWorld = ((cam72cam.mod.world.World) entity.getWorld()).internal;
            Class<?> entityVehicleClass = Class.forName("com.flansmod.common.driveables.EntityVehicle");
            Constructor<?> vehicleCtor = entityVehicleClass.getConstructor(
                    net.minecraft.world.World.class, double.class, double.class, double.class, float.class,
                    vehicleTypeClass, Class.forName("com.flansmod.common.driveables.DriveableData"));
            Object vehicle = vehicleCtor.newInstance(mcWorld, entity.getPosition().x, entity.getPosition().y + 1.5, entity.getPosition().z,
                    cargo.rotationYaw, type, restoreDriveableData(cargo));

            if (cargo.ownerName != null) {
                Class<?> factionsClass = Class.forName("com.hfr.faction.Factions");
                Method getFaction = factionsClass.getMethod("getFactionFromName", String.class);
                Object faction = getFaction.invoke(null, cargo.ownerName);
                Method setOwner = entityVehicleClass.getMethod("setOwner", faction != null ? faction.getClass() : Object.class);
                setOwner.invoke(vehicle, faction);
            }

            Field lockedField = entityVehicleClass.getField("locked");
            lockedField.set(vehicle, cargo.locked);
            Field stolenField = entityVehicleClass.getField("stolen");
            stolenField.set(vehicle, cargo.stolen);

            Class<?> driveableDataClass = Class.forName("com.flansmod.common.driveables.DriveableData");
            Field partsField = driveableDataClass.getField("parts");
            Map parts = (Map) partsField.get(vehicle.getClass().getMethod("getDriveableData").invoke(vehicle));

            Class<?> enumDriveablePartClass = Class.forName("com.flansmod.common.driveables.EnumDriveablePart");
            Method valuesMethod = enumDriveablePartClass.getMethod("values");
            Object[] partsEnum = valuesMethod.invoke(null);
            Field healthField = Class.forName("com.flansmod.common.driveables.DriveablePart").getField("health");
            Field maxHealthField = Class.forName("com.flansmod.common.driveables.DriveablePart").getField("maxHealth");
            Field onFireField = Class.forName("com.flansmod.common.driveables.DriveablePart").getField("onFire");
            Field crewField = Class.forName("com.flansmod.common.driveables.DriveablePart").getField("crew");

            for (int i = 0; i < partsEnum.length; i++) {
                Object part = parts.get(partsEnum[i]);
                if (part != null && i < cargo.partsHealth.length) {
                    healthField.set(part, cargo.partsHealth[i]);
                    maxHealthField.set(part, cargo.partsMaxHealth[i]);
                    onFireField.set(part, cargo.partsOnFire[i]);
                    crewField.set(part, cargo.partsCrew[i]);
                }
            }

            Method spawnMethod = net.minecraft.world.World.class.getMethod("spawnEntityInWorld", net.minecraft.entity.Entity.class);
            spawnMethod.invoke(mcWorld, (net.minecraft.entity.Entity) vehicle);

            Field seatsField = entityVehicleClass.getField("seats");
            Object[] seats = (Object[]) seatsField.get(vehicle);
            if (seats != null && seats.length > 0) {
                Object seatEntity = seats[0];
                Method mountMethod = player.getClass().getMethod("mountEntity", seatEntity.getClass());
                mountMethod.invoke(player, seatEntity);
            }
        } catch (Exception e) {
            ImmersiveRailroading.catching(e);
        }
    }

    private static Object restoreDriveableData(FlanVehicleCargo cargo) throws Exception {
        Class<?> driveableDataClass = Class.forName("com.flansmod.common.driveables.DriveableData");
        Constructor<?> ctor = driveableDataClass.getConstructor(NBTTagCompound.class);
        Object data = ctor.newInstance(new NBTTagCompound());

        setField(data, "paintjobID", cargo.paintjobID);
        setField(data, "fuelInTank", cargo.fuelInTank);

        if (cargo.fuel != null) {
            setField(data, "fuel", net.minecraft.item.ItemStack.loadItemStackFromNBT(cargo.fuel));
        }

        if (cargo.ammo != null) {
            Object[] ammo = new Object[cargo.ammo.length];
            for (int i = 0; i < cargo.ammo.length; i++) {
                if (cargo.ammo[i] != null) {
                    ammo[i] = net.minecraft.item.ItemStack.loadItemStackFromNBT(cargo.ammo[i]);
                }
            }
            setField(data, "ammo", ammo);
        }

        if (cargo.bombs != null) {
            Object[] bombs = new Object[cargo.bombs.length];
            for (int i = 0; i < cargo.bombs.length; i++) {
                if (cargo.bombs[i] != null) {
                    bombs[i] = net.minecraft.item.ItemStack.loadItemStackFromNBT(cargo.bombs[i]);
                }
            }
            setField(data, "bombs", bombs);
        }

        if (cargo.missiles != null) {
            Object[] missiles = new Object[cargo.missiles.length];
            for (int i = 0; i < cargo.missiles.length; i++) {
                if (cargo.missiles[i] != null) {
                    missiles[i] = net.minecraft.item.ItemStack.loadItemStackFromNBT(cargo.missiles[i]);
                }
            }
            setField(data, "missiles", missiles);
        }

        if (cargo.cargo != null) {
            Object[] cargoItems = new Object[cargo.cargo.length];
            for (int i = 0; i < cargo.cargo.length; i++) {
                if (cargo.cargo[i] != null) {
                    cargoItems[i] = net.minecraft.item.ItemStack.loadItemStackFromNBT(cargo.cargo[i]);
                }
            }
            setField(data, "cargo", cargoItems);
        }

        setField(data, "seatBelt", cargo.seatBelt);
        setField(data, "emergencyMode", cargo.emergencyMode);
        setField(data, "WarpLimit", cargo.warpLimit);

        return data;
    }

    private static void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
