package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.entity.FlanVehicleCargo;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.render.opengl.RenderState;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class FlanVehicleRenderer {
    private final List<ModelComponent> components;
    public final List<IBoundingBox> boxes;
    private Object cachedModel;
    private long lastUpdateTick = -1;
    private String lastVehicleType = "";
    private int lastPaintjobID = -1;

    public List<ModelComponent> getComponents() {
        return components;
    }

    public static FlanVehicleRenderer get(ComponentProvider provider) {
        List<ModelComponent> found = provider.parseAll(ModelComponentType.FLAN_VEHICLE);
        if (found.isEmpty()) {
            return null;
        }
        try {
            Class.forName("com.flansmod.client.model.ModelVehicle");
            Class.forName("com.flansmod.client.tmt.ModelRendererTurbo");
            Class.forName("com.flansmod.common.driveables.VehicleType");
        } catch (ClassNotFoundException e) {
            return null;
        }
        return new FlanVehicleRenderer(found);
    }

    public FlanVehicleRenderer(List<ModelComponent> components) {
        this.boxes = new java.util.ArrayList<>();
        this.components = components;
        this.components.forEach(component -> boxes.add(IBoundingBox.from(component.min, component.max)));
    }

    public <T extends Freight> void postRender(T stock, RenderState state, float partialTicks) {
        FlanVehicleCargo cargo = stock.getFlanVehicleCargo();
        if (cargo == null || !cargo.hasVehicle()) {
            return;
        }

        if (stock.getWorld().getTicks() > lastUpdateTick + 40) {
            cachedModel = null;
            lastUpdateTick = stock.getWorld().getTicks();
        }

        if (cachedModel == null || !lastVehicleType.equals(cargo.vehicleType) || lastPaintjobID != cargo.paintjobID) {
            rebuildModel(cargo);
            lastVehicleType = cargo.vehicleType;
            lastPaintjobID = cargo.paintjobID;
        }

        if (cachedModel != null) {
            for (ModelComponent comp : components) {
                Matrix4 matrix = new Matrix4()
                        .translate(comp.center.x, comp.min.y, comp.center.z)
                        .scale(1.0 / stock.gauge.scale(), 1.0 / stock.gauge.scale(), 1.0 / stock.gauge.scale())
                        .rotate(Math.toRadians(-90), 0, 1, 0)
                        .rotate(Math.toRadians(cargo.rotationYaw), 0, 1, 0);

                state.pushMatrix();
                matrix.applyGL();
                try {
                    renderVehicleStatic(cargo);
                } catch (Exception e) {
                    cachedModel = null;
                }
                state.popMatrix();
            }
        }
    }

    private void rebuildModel(FlanVehicleCargo cargo) {
        try {
            Class<?> vehicleTypeClass = Class.forName("com.flansmod.common.driveables.VehicleType");
            Method getVehicle = vehicleTypeClass.getMethod("getVehicle", String.class);
            Object type = getVehicle.invoke(null, cargo.vehicleType);
            if (type == null) {
                cachedModel = null;
                return;
            }
            Field modelField = vehicleTypeClass.getField("model");
            cachedModel = modelField.get(type);
        } catch (Exception e) {
            cachedModel = null;
        }
    }

    private void renderVehicleStatic(FlanVehicleCargo cargo) throws Exception {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        Field textureManagerField = mc.getClass().getDeclaredField("textureManager");
        textureManagerField.setAccessible(true);
        Object textureManager = textureManagerField.get(mc);

        Class<?> vehicleTypeClass = Class.forName("com.flansmod.common.driveables.VehicleType");
        Method getVehicle = vehicleTypeClass.getMethod("getVehicle", String.class);
        Object type = getVehicle.invoke(null, cargo.vehicleType);
        if (type != null) {
            try {
                Class<?> resHandlerClass = Class.forName("com.flansmod.client.FlansModResourceHandler");
                Method getPaintjobTexture = resHandlerClass.getMethod("getPaintjobTexture", Class.forName("com.flansmod.common.driveables.DriveableType"), int.class);
                Object texture = getPaintjobTexture.invoke(null, type, cargo.paintjobID);
                if (texture instanceof ResourceLocation) {
                    Method bindTexture = textureManager.getClass().getMethod("bindTexture", ResourceLocation.class);
                    bindTexture.invoke(textureManager, texture);
                }
            } catch (Exception e) {
            }
        }

        renderModelField(cachedModel, "bodyModel");
        renderModelField(cachedModel, "bodyDoorCloseModel");
        renderModelField(cachedModel, "turretModel");
        renderModelField(cachedModel, "barrelModel");
        renderModelField(cachedModel, "trailerModel");
        renderModelField(cachedModel, "leftBackWheelModel");
        renderModelField(cachedModel, "rightBackWheelModel");
        renderModelField(cachedModel, "leftFrontWheelModel");
        renderModelField(cachedModel, "rightFrontWheelModel");
        renderModelField(cachedModel, "frontWheelModel");
        renderModelField(cachedModel, "backWheelModel");
        renderModelField(cachedModel, "leftTrackModel");
        renderModelField(cachedModel, "rightTrackModel");
        renderModelField(cachedModel, "leftTrackWheelModels");
        renderModelField(cachedModel, "rightTrackWheelModels");
        renderAnimTrackField(cachedModel, "leftAnimTrackModel");
        renderAnimTrackField(cachedModel, "rightAnimTrackModel");
    }

    private void renderModelField(Object model, String fieldName) throws Exception {
        Field field = model.getClass().getField(fieldName);
        Object parts = field.get(model);
        if (parts == null) return;
        renderParts(parts);
    }

    private void renderAnimTrackField(Object model, String fieldName) throws Exception {
        Field field = model.getClass().getField(fieldName);
        Object trackArrays = field.get(model);
        if (trackArrays == null || Array.getLength(trackArrays) == 0) return;
        Object firstArray = Array.get(trackArrays, 0);
        if (firstArray == null) return;
        renderParts(firstArray);
    }

    private void renderParts(Object parts) throws Exception {
        Class<?> rendererClass = Class.forName("com.flansmod.client.tmt.ModelRendererTurbo");
        Method renderMethod = rendererClass.getMethod("render", float.class, int.class);
        Field oldRotateOrderField = rendererClass.getField("oldRotateOrder");
        int length = Array.getLength(parts);
        for (int i = 0; i < length; i++) {
            Object part = Array.get(parts, i);
            int oldRotateOrder = oldRotateOrderField.getInt(part);
            renderMethod.invoke(part, 0.0625f, oldRotateOrder);
        }
    }
}
