package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.entity.FlanVehicleCargo;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.render.opengl.RenderState;
import com.flansmod.client.model.ModelDriveable;
import com.flansmod.client.model.ModelVehicle;
import com.flansmod.client.tmt.ModelRendererTurbo;
import com.flansmod.common.driveables.VehicleType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import org.lwjgl.opengl.GL11;
import util.Matrix4;

import java.util.List;

public class FlanVehicleRenderer {
    private final List<ModelComponent> components;
    public final List<IBoundingBox> boxes;
    private ModelVehicle cachedModel;
    private long lastUpdateTick = -1;
    private String lastVehicleType = "";
    private int lastPaintjobID = -1;

    public List<ModelComponent> getComponents() {
        return components;
    }

    public static FlanVehicleRenderer get(ComponentProvider provider) {
        List<ModelComponent> found = provider.parseAll(ModelComponentType.FLAN_VEHICLE);
        return found.isEmpty() ? null : new FlanVehicleRenderer(found);
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
                renderVehicleStatic(cachedModel, cargo);
                state.popMatrix();
            }
        }
    }

    private void rebuildModel(FlanVehicleCargo cargo) {
        VehicleType type = VehicleType.getVehicle(cargo.vehicleType);
        if (type == null || type.model == null) {
            cachedModel = null;
            return;
        }

        cachedModel = (ModelVehicle) type.model;
    }

    private void renderVehicleStatic(ModelVehicle model, FlanVehicleCargo cargo) {
        TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();
        VehicleType type = VehicleType.getVehicle(cargo.vehicleType);
        if (type != null) {
            textureManager.bindTexture(com.flansmod.client.FlansModResourceHandler.getPaintjobTexture(type.getPaintjob(cargo.paintjobID)));
        }

        GL11.glPushMatrix();
        GL11.glScalef(cargo.rotationPitch != 0 ? 1.0f : 1.0f, 1.0f, 1.0f);
        
        // Render body model
        for (com.flansmod.client.tmt.ModelRendererTurbo bodyModel : model.bodyModel) {
            bodyModel.render(0.0625f, model.oldRotateOrder);
        }
        
        // Render doors (default closed)
        for (com.flansmod.client.tmt.ModelRendererTurbo doorModel : model.bodyDoorCloseModel) {
            doorModel.render(0.0625f, model.oldRotateOrder);
        }
        
        // Render turret and barrel (static)
        if (model.turretModel != null && model.turretModel.length > 0) {
            GL11.glPushMatrix();
            GL11.glScalef(model.turretScale.x, model.turretScale.y, model.turretScale.z);
            GL11.glTranslatef(model.turretTrans.x, model.turretTrans.y, model.turretTrans.z);
            for (com.flansmod.client.tmt.ModelRendererTurbo turretPart : model.turretModel) {
                turretPart.render(0.0625f, model.oldRotateOrder);
            }
            for (com.flansmod.client.tmt.ModelRendererTurbo barrelPart : model.barrelModel) {
                barrelPart.render(0.0625f, model.oldRotateOrder);
            }
            GL11.glPopMatrix();
        }
        
        // Render wheels (static)
        renderWheelsStatic(model);
        
        // Render tracks (static)
        renderTracksStatic(model);
        
        // Render trailer
        for (com.flansmod.client.tmt.ModelRendererTurbo trailerPart : model.trailerModel) {
            trailerPart.render(0.0625f, model.oldRotateOrder);
        }
        
        GL11.glPopMatrix();
    }
    
    private void renderWheelsStatic(ModelVehicle model) {
        for (com.flansmod.client.tmt.ModelRendererTurbo wheel : model.leftBackWheelModel) {
            wheel.render(0.0625f, model.oldRotateOrder);
        }
        for (com.flansmod.client.tmt.ModelRendererTurbo wheel : model.rightBackWheelModel) {
            wheel.render(0.0625f, model.oldRotateOrder);
        }
        for (com.flansmod.client.tmt.ModelRendererTurbo wheel : model.leftFrontWheelModel) {
            wheel.render(0.0625f, model.oldRotateOrder);
        }
        for (com.flansmod.client.tmt.ModelRendererTurbo wheel : model.rightFrontWheelModel) {
            wheel.render(0.0625f, model.oldRotateOrder);
        }
        for (com.flansmod.client.tmt.ModelRendererTurbo wheel : model.frontWheelModel) {
            wheel.render(0.0625f, model.oldRotateOrder);
        }
        for (com.flansmod.client.tmt.ModelRendererTurbo wheel : model.backWheelModel) {
            wheel.render(0.0625f, model.oldRotateOrder);
        }
    }
    
    private void renderTracksStatic(ModelVehicle model) {
        for (com.flansmod.client.tmt.ModelRendererTurbo track : model.leftTrackModel) {
            track.render(0.0625f, model.oldRotateOrder);
        }
        for (com.flansmod.client.tmt.ModelRendererTurbo track : model.rightTrackModel) {
            track.render(0.0625f, model.oldRotateOrder);
        }
        for (com.flansmod.client.tmt.ModelRendererTurbo wheel : model.leftTrackWheelModels) {
            wheel.render(0.0625f, model.oldRotateOrder);
        }
        for (com.flansmod.client.tmt.ModelRendererTurbo wheel : model.rightTrackWheelModels) {
            wheel.render(0.0625f, model.oldRotateOrder);
        }
        // Render first animation frame of tracks
        if (model.leftAnimTrackModel != null && model.leftAnimTrackModel.length > 0 && model.leftAnimTrackModel[0] != null) {
            for (com.flansmod.client.tmt.ModelRendererTurbo track : model.leftAnimTrackModel[0]) {
                track.render(0.0625f, model.oldRotateOrder);
            }
        }
        if (model.rightAnimTrackModel != null && model.rightAnimTrackModel.length > 0 && model.rightAnimTrackModel[0] != null) {
            for (com.flansmod.client.tmt.ModelRendererTurbo track : model.rightAnimTrackModel[0]) {
                track.render(0.0625f, model.oldRotateOrder);
            }
        }
    }
}