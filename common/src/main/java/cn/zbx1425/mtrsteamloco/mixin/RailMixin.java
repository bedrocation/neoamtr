package cn.zbx1425.mtrsteamloco.mixin;

import cn.zbx1425.mtrsteamloco.ClientConfig;
import cn.zbx1425.mtrsteamloco.Main;
import cn.zbx1425.mtrsteamloco.data.RailExtraSupplier;
import cn.zbx1425.mtrsteamloco.data.RailModelRegistry;
import net.minecraft.core.BlockPos;
import cn.zbx1425.mtrsteamloco.render.rail.RailRenderDispatcher;
import io.netty.buffer.Unpooled;
import mtr.data.MessagePackHelper;
import mtr.data.Rail;
import cn.zbx1425.mtrsteamloco.data.ConfigResponder;
import cn.zbx1425.mtrsteamloco.network.util.StringMapSerializer;
import cn.zbx1425.mtrsteamloco.network.util.DoubleFloatMapSerializer;
import net.minecraft.world.phys.Vec3;
import cn.zbx1425.mtrsteamloco.data.RailCalculator.Vec2;
import mtr.data.RailType;
import net.minecraft.network.FriendlyByteBuf;
import cn.zbx1425.mtrsteamloco.data.RailCalculator;
import net.minecraft.util.Mth;
import mtr.data.RailAngle;
import mtr.data.TransportMode;
import cn.zbx1425.mtrsteamloco.block.BlockDirectNode.BlockEntityDirectNode;
import cn.zbx1425.mtrsteamloco.data.RailAngleExtra;

import org.msgpack.core.MessagePacker;
import org.msgpack.value.Value;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

@Mixin(value = Rail.class, priority = 1425)
public abstract class RailMixin implements RailExtraSupplier {

    private static final double ACCEPT_THRESHOLD = 1E-4;
    @Shadow(remap = false) @Final @Mutable private RailType railType;
    @Shadow(remap = false) @Final @Mutable private TransportMode transportMode;
    @Shadow(remap = false) @Final @Mutable private RailAngle facingStart;
    @Shadow(remap = false) @Final @Mutable private RailAngle facingEnd;
    @Shadow(remap = false) @Final @Mutable private double h1, k1, r1, tStart1, tEnd1;
    @Shadow(remap = false) @Final @Mutable private double h2, k2, r2, tStart2, tEnd2;
    @Shadow(remap = false) @Final @Mutable private int yStart, yEnd;
    @Shadow(remap = false) @Final @Mutable private boolean reverseT1, isStraight1, reverseT2, isStraight2;

    private static double getTBounds(double x, double h, double z, double k, double r) {
        return Mth.atan2(z - k, x - h) * r;
    }

    private static double getTBounds(double x, double h, double z, double k, double r, double tStart, boolean reverse) {
        final double t = getTBounds(x, h, z, k, r);
        if (t < tStart && !reverse) {
            return t + 2 * Math.PI * r;
        } else if (t > tStart && reverse) {
            return t - 2 * Math.PI * r;
        } else {
            return t;
        }
    }

    private String modelKey = "";
    private boolean isSecondaryDir = false;
    private float verticalCurveRadius = 0f;
    private Map<String, String> customConfigs = new HashMap<>();
    private Map<String, ConfigResponder> customResponders = new HashMap<>();
    private Map<Double, Float> rollAngleMap = new HashMap<>();
    private int openingDirection = 0;// 0 不开 1 左 2 右 3 双向
    private float rollingOffset = 1.435F / 2F;

    @Override
    public String getModelKey() {
        return modelKey;
    }

    @Override
    public void setModelKey(String key) {
        this.modelKey = key;
    }

    @Override
    public boolean getRenderReversed() {
        return isSecondaryDir;
    }

    @Override
    public void setRenderReversed(boolean value) {
        this.isSecondaryDir = value;
    }

    @Override
    public float getVerticalCurveRadius() {
        return verticalCurveRadius;
    }

    @Override
    public void setVerticalCurveRadius(float value) {
        this.verticalCurveRadius = value;
    }

    @Override
    public int getHeight() {
        return yEnd - yStart;
    }

    @Override
    public Map<Double, Float> getRollAngleMap() {
        return rollAngleMap;
    }

    @Override
    public void setRollAngleMap(Map<Double, Float> rollAngleMap) {
        this.rollAngleMap = rollAngleMap;
    }

    @Override
    public Map<String, String> getCustomConfigs() {
        return customConfigs;
    }

    @Override
    public void setCustomConfigs(Map<String, String> customConfigs) {
        this.customConfigs = customConfigs;
    }

    @Override
    public Map<String, ConfigResponder> getCustomResponders() {
        return customResponders;
    }

    @Override
    public void setCustomResponders(Map<String, ConfigResponder> customResponders) {
        this.customResponders = customResponders;
    }

    public void setOpeningDirectionRaw(int direction) {
        this.openingDirection = direction;
    }

    @Override
    public void setOpeningDirection(int direction) {
        if (getRenderReversed()) {
            if (direction == 1) direction = 2;
            else if (direction == 2) direction = 1;
        }
        this.openingDirection = direction;
    }

    @Override
    public int getOpeningDirection() {
        int direction = openingDirection;
        if (getRenderReversed()) {
            if (direction == 1) direction = 2;
            else if (direction == 2) direction = 1;
        }
        return direction;
    }

    @Override
    public int getOpeningDirectionRaw() {
        return openingDirection;
    }

    @Override
    public void setRailType(RailType railType) {
        this.railType = railType;
    }

    @Override
    public void partialCopyFrom(Rail other) {
        RailExtraSupplier oth = (RailExtraSupplier) other;
        setModelKey(oth.getModelKey());
        // setRenderReversed(oth.getRenderReversed());
        setVerticalCurveRadius(oth.getVerticalCurveRadius());
        setCustomConfigs(oth.getCustomConfigs());
        double length0 = other.getLength();
        double length1 = ((Rail) (Object) this).getLength();
        double k = length1 / length0;
        Map<Double, Float> src = new HashMap<>(oth.getRollAngleMap());
        Map<Double, Float> dst = new HashMap<>();
        for (Map.Entry<Double, Float> entry : src.entrySet()) {
            dst.put(entry.getKey() * k, entry.getValue());
        }
        setRollAngleMap(dst);
        setOpeningDirection(oth.getOpeningDirection());
        setRollingOffset(oth.getRollingOffset());
    }

    @Override
    public void setRollingOffset(float rollingOffset) {
        this.rollingOffset = rollingOffset;
    }

    @Override
    public float getRollingOffset() {
        return rollingOffset;
    }

    @Inject(method = "<init>(Lnet/minecraft/core/BlockPos;Lmtr/data/RailAngle;Lnet/minecraft/core/BlockPos;Lmtr/data/RailAngle;Lmtr/data/RailType;Lmtr/data/TransportMode;)V", at = @At("TAIL"))
    private void onCreate(BlockPos posStart, RailAngle facingStart, BlockPos posEnd, RailAngle facingEnd, RailType railType, TransportMode transportMode, CallbackInfo ci) {

        final int xStart = posStart.getX();
        final int zStart = posStart.getZ();
        final int xEnd = posEnd.getX();
        final int zEnd = posEnd.getZ();

        if (transportMode == TransportMode.TRAIN) {
            RailCalculator.Group group = RailCalculator.calculate(xStart, zStart, xEnd, zEnd, facingStart.angleRadians, facingEnd.angleRadians);

            if (group != null) {
                h1 = group.first.h;
                k1 = group.first.k;
                r1 = group.first.r;
                tStart1 = group.first.tStart;
                tEnd1 = group.first.tEnd;
                reverseT1 = group.first.reverseT;
                isStraight1 = group.first.isStraight;

                h2 = group.second.h;
                k2 = group.second.k;
                r2 = group.second.r;
                tStart2 = group.second.tStart;
                tEnd2 = group.second.tEnd;
                reverseT2 = group.second.reverseT;
                isStraight2 = group.second.isStraight;

                this.facingStart = getRailAngle(false);
                this.facingEnd = getRailAngle(true);
            }
        }
    }

    @Shadow(remap = false) public abstract double getLength();
    @Shadow(remap = false) public abstract Vec3 getPosition(double distance);
    @Shadow(remap = false) public abstract RailAngle getRailAngle(boolean getEnd);

    @Inject(method = "getRailAngle", at = @At("HEAD"), cancellable = true, remap = false)
    private void getRailAngle(boolean getEnd, CallbackInfoReturnable<RailAngle> cir) {
        final double start;
        final double end;
        if (getEnd) {
            start = getLength();
            end = start - ACCEPT_THRESHOLD;
        } else {
            start = 0;
            end = ACCEPT_THRESHOLD;
        }
        final Vec3 pos1 = getPosition(start);
        final Vec3 pos2 = getPosition(end);
        RailAngle result =  RailAngleExtra.fromRadians(Math.atan2(pos2.z - pos1.z, pos2.x - pos1.x));
        cir.setReturnValue(result);
        cir.cancel();
        return;
    }

    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true, remap = false)
    private void isValid(CallbackInfoReturnable<Boolean> cir) {
        boolean b1 = (h1 != 0 || k1 != 0 || h2 != 0 || k2 != 0 || r1 != 0 || r2 != 0 || tStart1 != 0 || tStart2 != 0 || tEnd1 != 0 || tEnd2 != 0);
        RailAngle f1 = getRailAngle(false);
        RailAngle f2 = getRailAngle(true);
        boolean b2 = false;
        boolean b3 = false;
        float f3 = f1.angleDegrees - facingStart.angleDegrees;
        float f4 = f2.angleDegrees - facingEnd.angleDegrees;
        f3 = Math.abs(f3) % 180;
        f4 = Math.abs(f4) % 180;
        if (f3 < 0.3) b2 = true;
        if (f4 < 0.3) b3 = true;
        b2 = b2 && b3;
        // Main.LOGGER.info("isValid: " + b1 + " " + b2 + " "+ f1 + " " + f2 + " * " + facingStart + " " + facingEnd + " ** " + f3 + " " + f4);
        cir.setReturnValue(b1);// && b2);
        cir.cancel();
        return;
    }

    @Inject(method = "<init>(Ljava/util/Map;)V", at = @At("TAIL"), remap = false)
    private void fromMessagePack(Map<String, Value> map, CallbackInfo ci) {
        MessagePackHelper messagePackHelper = new MessagePackHelper(map);
        modelKey = messagePackHelper.getString("model_key", "");
        isSecondaryDir = messagePackHelper.getBoolean("is_secondary_dir", false);
        verticalCurveRadius = messagePackHelper.getFloat("vertical_curve_radius", 0);
        try {
            customConfigs = StringMapSerializer.deserialize(messagePackHelper.getString("custom_configs"));
        } catch (IOException e) {
            customConfigs = new HashMap<>();
        }
        try {
            rollAngleMap = DoubleFloatMapSerializer.deserialize(messagePackHelper.getString("roll_angle_map"));
        } catch (Exception e) {
            rollAngleMap = new HashMap<>();
        }
        openingDirection = messagePackHelper.getInt("opening_direction", 0);
        rollingOffset = messagePackHelper.getFloat("rolling_offset", 1.435F / 2F);
    }

    @Inject(method = "toMessagePack", at = @At("TAIL"), remap = false)
    private void toMessagePack(MessagePacker messagePacker, CallbackInfo ci) throws IOException {
        messagePacker.packString("model_key").packString(modelKey);
        messagePacker.packString("is_secondary_dir").packBoolean(isSecondaryDir);
        messagePacker.packString("vertical_curve_radius").packFloat(verticalCurveRadius);
        String res;
        try {
            res = StringMapSerializer.serializeToString(customConfigs);
        } catch (IOException e) {
            res = "";
        }
        messagePacker.packString("custom_configs").packString(res);
        String res2;
        try {
            res2 = DoubleFloatMapSerializer.serializeToString(rollAngleMap);
        } catch (Exception e) {
            res2 = "";
        }
        messagePacker.packString("roll_angle_map").packString(res2);
        messagePacker.packString("opening_direction").packInt(openingDirection);
        messagePacker.packString("rolling_offset").packFloat(rollingOffset);
    }

    @Inject(method = "messagePackLength", at = @At("TAIL"), cancellable = true, remap = false)
    private void messagePackLength(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() + 7);
    }

    private final int NTE_PACKET_EXTRA_MAGIC = 0x25141425;

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("TAIL"))
    private void fromPacket(FriendlyByteBuf packet, CallbackInfo ci) {
        if (!Main.enableRegistry) return;
        if (packet.readableBytes() <= 4) return;
        if (packet.readInt() != NTE_PACKET_EXTRA_MAGIC) {
            packet.readerIndex(packet.readerIndex() - 4);
            return;
        }
        modelKey = packet.readUtf();
        isSecondaryDir = packet.readBoolean();
        verticalCurveRadius = packet.readFloat();
        try {
            customConfigs = StringMapSerializer.deserialize(packet.readUtf());
        } catch (IOException e) {
            customConfigs = new HashMap<>();
        }
        try {
            rollAngleMap = DoubleFloatMapSerializer.deserialize(packet.readUtf());
        } catch (Exception e) {
            rollAngleMap = new HashMap<>();
        }
        openingDirection = packet.readInt();
        rollingOffset = packet.readFloat();
    }

    @Inject(method = "writePacket", at = @At("TAIL"))
    private void toPacket(FriendlyByteBuf packet, CallbackInfo ci) {
        if (!Main.enableRegistry) return;
        packet.writeInt(NTE_PACKET_EXTRA_MAGIC);
        packet.writeUtf(modelKey);
        packet.writeBoolean(isSecondaryDir);
        packet.writeFloat(verticalCurveRadius);
        String res;
        try {
            res = StringMapSerializer.serializeToString(customConfigs);
        } catch (IOException e) {
            res = "";
        }
        packet.writeUtf(res);
        String res2;
        try {
            res2 = DoubleFloatMapSerializer.serializeToString(rollAngleMap);
        } catch (Exception e) {
            res2 = "";
        }
        packet.writeUtf(res2);
        packet.writeInt(openingDirection);
        packet.writeFloat(rollingOffset);
    }

    @Redirect(method = "renderSegment", remap = false, at = @At(value = "INVOKE", target = "Ljava/lang/Math;round(D)J"))
    private long redirectRenderSegmentRound(double r) {
        if (ClientConfig.getRailRenderLevel() < 2) return Math.round(r);

        Rail instance = (Rail)(Object)this;
        if (instance.railType == RailType.NONE) {
            return Math.round(r);
        } else {
            return Math.round(r / RailModelRegistry.getProperty(RailRenderDispatcher.getModelKeyForRender(instance)).repeatInterval);
        }
    }

    private float vTheta;

    private static final int CABLE_CURVATURE_SCALE = 1000;
    private static final int MAX_CABLE_DIP = 8;

    private double _getPositionY(double value) {
        final double length = getLength();

        if (railType.railSlopeStyle == RailType.RailSlopeStyle.CABLE) {
            if (value < 0.5) {
                return yStart;
            } else if (value > length - 0.5) {
                return yEnd;
            }

            final double offsetValue = value - 0.5;
            final double offsetLength = length - 1;
            final double posY = yStart + (yEnd - yStart) * offsetValue / offsetLength;
            final double dip = offsetLength * offsetLength / 4 / CABLE_CURVATURE_SCALE;
            return posY + (dip > MAX_CABLE_DIP ? MAX_CABLE_DIP / dip : 1) * (offsetValue - offsetLength) * offsetValue / CABLE_CURVATURE_SCALE;
        } else {
            final double intercept = length / 2;
            final double yChange;
            final double yInitial;
            final double offsetValue;

            if (value < intercept) {
                yChange = (yEnd - yStart) / 2D;
                yInitial = yStart;
                offsetValue = value;
            } else {
                yChange = (yStart - yEnd) / 2D;
                yInitial = yEnd;
                offsetValue = length - value;
            }

            return yChange * offsetValue * offsetValue / (intercept * intercept) + yInitial;
        }
    }

    @Inject(method = "getPositionY", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetPositionY(double rawValue, CallbackInfoReturnable<Double> cir) {
        if (((Rail)(Object)this).railType.railSlopeStyle == RailType.RailSlopeStyle.CABLE) return;
        double H = Math.abs(yEnd - yStart);
        double L = ((Rail)(Object)this).getLength();
        int sign = yStart < yEnd ? 1 : -1;
        double maxRadius = (H == 0) ? 0 : Math.abs((H * H + L * L) / (H * 4));
        double result = 0d;
        if (verticalCurveRadius < 0) {
            // Magic value for a flat rail
            result = sign * (rawValue / L) * H + yStart;
        } else if (verticalCurveRadius == 0 || verticalCurveRadius > maxRadius) {
            // Magic default value / impossible radius, fallback to MTR all curvy track
            result  = _getPositionY(rawValue);
        } else {
            if (vTheta == 0) vTheta = RailExtraSupplier.getVTheta((Rail)(Object)this, verticalCurveRadius);
            if (!Double.isFinite(vTheta)) {
                result = _getPositionY(rawValue);
            } else {
                float curveL = Mth.sin(vTheta) * verticalCurveRadius;
                float curveH = (1 - Mth.cos(vTheta)) * verticalCurveRadius;
                if (rawValue < curveL) {
                    float r = (float)rawValue;
                    result = sign * (verticalCurveRadius - Math.sqrt(verticalCurveRadius * verticalCurveRadius - r * r)) + yStart;
                } else if (rawValue > L - curveL) {
                    float r = (float)(L - rawValue);
                    result = -sign * (verticalCurveRadius - Math.sqrt(verticalCurveRadius * verticalCurveRadius - r * r)) + yEnd;
                } else {
                    result = sign * (((rawValue - curveL) / (L - 2 * curveL)) * (H - 2 * curveH) + curveH) + yStart;
                }
            }
        }
        float r = RailExtraSupplier.getRollAngle((Rail) (Object) this, rawValue);
        float offset = getRollingOffset();
        if (r != 0) {
            double dy = new Vec2(offset, 0).rotateRad(r).z;
            result += Math.abs(dy);
        }
        cir.setReturnValue(result);
        cir.cancel();
        return;
    }

    private static final FriendlyByteBuf hashBuilder = new FriendlyByteBuf(Unpooled.buffer());
    private byte[] dataBytes;
    private int hashCode;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (dataBytes == null) createDataBytes();
        if (((RailMixin)o).dataBytes == null) ((RailMixin)o).createDataBytes();
        return Arrays.equals(dataBytes, ((RailMixin)o).dataBytes);
    }

    @Override
    public int hashCode() {
        if (dataBytes == null) createDataBytes();
        return hashCode;
    }

    private void createDataBytes() {
        hashBuilder.clear();
        ((Rail)(Object)this).writePacket(hashBuilder);
        dataBytes = new byte[hashBuilder.writerIndex()];
        hashBuilder.getBytes(0, dataBytes);
        hashCode = Arrays.hashCode(dataBytes);
    }
}
