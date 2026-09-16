package cam72cam.immersiverailroading.entity;

import cam72cam.mod.serialization.TagField;
import cam72cam.mod.serialization.TagSync;
import net.minecraft.nbt.NBTTagCompound;

public class FlanVehicleCargo {
    @TagField("vehicleType")
    public String vehicleType;

    @TagField("paintjobID")
    public int paintjobID;

    @TagField("fuelInTank")
    public float fuelInTank;

    @TagField("fuel")
    public NBTTagCompound fuel;

    @TagField("ammo")
    public NBTTagCompound[] ammo;

    @TagField("bombs")
    public NBTTagCompound[] bombs;

    @TagField("missiles")
    public NBTTagCompound[] missiles;

    @TagField("cargo")
    public NBTTagCompound[] cargo;

    @TagField("partsHealth")
    public int[] partsHealth;

    @TagField("partsMaxHealth")
    public int[] partsMaxHealth;

    @TagField("partsOnFire")
    public boolean[] partsOnFire;

    @TagField("partsCrew")
    public int[] partsCrew;

    @TagField("seatBelt")
    public String seatBelt;

    @TagField("emergencyMode")
    public boolean emergencyMode;

    @TagField("warpLimit")
    public int warpLimit;

    @TagField("rotationYaw")
    public float rotationYaw;

    @TagField("rotationPitch")
    public float rotationPitch;

    @TagField("rotationRoll")
    public float rotationRoll;

    @TagField("ownerName")
    public String ownerName;

    @TagField("locked")
    public boolean locked;

    @TagField("stolen")
    public boolean stolen;

    public FlanVehicleCargo() {}

    public boolean hasVehicle() {
        return vehicleType != null && !vehicleType.isEmpty();
    }

    public void clear() {
        vehicleType = null;
        paintjobID = 0;
        fuelInTank = 0;
        fuel = null;
        ammo = null;
        bombs = null;
        missiles = null;
        cargo = null;
        partsHealth = null;
        partsMaxHealth = null;
        partsOnFire = null;
        partsCrew = null;
        seatBelt = "null";
        emergencyMode = false;
        warpLimit = 1;
        rotationYaw = 0;
        rotationPitch = 0;
        rotationRoll = 0;
        ownerName = null;
        locked = false;
        stolen = false;
    }
}