package trackapi.lib;

import net.minecraft.util.Vec3;

public interface ITrackV2 {
	double[] getTrackGauges();

	<D extends PathingData> void getNextPosition(D pos, Vec3 velocity, double gauge);
}
