package cn.zbx1425.mtrsteamloco.render.scripting.rail;

import mtr.data.*;
import cn.zbx1425.mtrsteamloco.data.RailExtraSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import cn.zbx1425.mtrsteamloco.network.PacketUpdateRail;
import cn.zbx1425.sowcer.math.Vector3f;

import java.util.Map;

public class RailWrapper{
    private final Rail rail;
    private final RailExtraSupplier supplier;
    public final RailType railType;
	public final TransportMode transportMode;
	public final RailAngle facingStart;
	public final RailAngle facingEnd;

    public RailWrapper(Rail rail) {
        this.rail = rail;
        this.supplier = (RailExtraSupplier) rail;
        this.railType = rail.railType;
        this.transportMode = rail.transportMode;
        this.facingStart = rail.facingStart;
        this.facingEnd = rail.facingEnd;
    }

    public Map<String, String> getCustomConfigs() {
        return supplier.getCustomConfigs();
    }

    public void setCustomConfigs(Map<String, String> customConfigs) {
        supplier.setCustomConfigs(customConfigs);
    }

    public Map<Double, Float> getRollAngleMap() {
        return supplier.getRollAngleMap();
    }

    public void setRollAngleMap(Map<Double, Float> rollAngleMap) {
        supplier.setRollAngleMap(rollAngleMap);
    }

    public void sendUpdateC2S() {
        double length = rail.getLength();
        BlockPos pos0 = getPosition(0).toBlockPos();
        BlockPos pos1 = getPosition(getLength()).toBlockPos();
        PacketUpdateRail.sendUpdateC2S(rail, pos0, pos1);
    }

    public double getLength() {
        return rail.getLength();
    }

    public Vector3f getPosition(double value) {
        return new Vector3f(rail.getPosition(value));
    }

    public float getRollAngle(double value) {
        return RailExtraSupplier.getRollAngle(rail, value);
    }

    public boolean getRenderReversed() {
        return supplier.getRenderReversed();
    }

    public Rail mtrRail() {
        return rail;
    }

    public int getOpeningDirection() {
        return supplier.getOpeningDirection();
    }

    public void setOpeningDirection(int openingDirection) {
        supplier.setOpeningDirection(openingDirection);
    }
}