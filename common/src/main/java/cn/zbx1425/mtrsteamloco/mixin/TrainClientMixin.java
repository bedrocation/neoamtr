package cn.zbx1425.mtrsteamloco.mixin;

import cn.zbx1425.mtrsteamloco.ClientConfig;
import cn.zbx1425.mtrsteamloco.render.train.NoopTrainRenderer;
import cn.zbx1425.mtrsteamloco.sound.NoopTrainSound;
import net.minecraft.network.FriendlyByteBuf;
import cn.zbx1425.mtrsteamloco.block.BlockEyeCandy;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import cn.zbx1425.mtrsteamloco.render.scripting.train.ScriptedTrainRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.player.LocalPlayer;
import mtr.path.PathData;
import mtr.sound.TrainSoundBase;
import cn.zbx1425.mtrsteamloco.data.TrainExtraSupplier;
// import cn.zbx1425.mtrsteamloco.data.VehicleRidingClientExtraSupplier;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.math.Matrices;
import net.minecraft.world.phys.Vec3;
import mtr.render.TrainRendererBase;
import mtr.data.TrainClient.*;
import mtr.data.*;
import mtr.client.*;
import mtr.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrainClient.class)
public abstract class TrainClientMixin extends Train implements IGui{

    public TrainClientMixin(
            long sidingId, float railLength,
            List<PathData> path, List<Double> distances, int repeatIndex1, int repeatIndex2,
            float accelerationConstant, boolean isManualAllowed, int maxManualSpeed, int manualToAutomaticTime,
            CompoundTag compoundTag
    ) {
        super(sidingId, railLength, path, distances, repeatIndex1, repeatIndex2, accelerationConstant, isManualAllowed, maxManualSpeed, manualToAutomaticTime, compoundTag);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void initTail(FriendlyByteBuf packet, CallbackInfo ci) {
        if (!ClientConfig.enableTrainRender) ((TrainClientAccessor) this).setTrainRenderer(NoopTrainRenderer.INSTANCE);
        if (!ClientConfig.enableTrainSound) ((TrainClientAccessor) this).setTrainSound(NoopTrainSound.INSTANCE);
    }

    @Inject(method = "copyFromTrain", at = @At("TAIL"), remap = false)
    private void copyFromTrainTail(Train other, CallbackInfo ci) {
        ((TrainExtraSupplier) (Object) this).setCustomConfigs(((TrainExtraSupplier) (Object) other).getCustomConfigs());
    }

    @Shadow(remap = false) private PerlinNoise1D irregX;
    @Shadow(remap = false) private PerlinNoise1D irregY;
    @Shadow(remap = false) private PerlinNoise1D irregR;

    @Inject(method = "renderCar", at = @At("HEAD"), cancellable = true, remap = false)
    private void onRenderCar(Level world, int ridingCar, float ticksElapsed,
                             double carX, double carY, double carZ, float carYaw, float carPitch, float carRoll,
                             double prevCarX, double prevCarY, double prevCarZ, float prevCarYaw, float prevCarPitch, float prevCarRoll,
                             boolean doorLeftOpen, boolean doorRightOpen, double realSpacing, CallbackInfo ci) {
        if (!world.isClientSide()) {
            return;
        }

        final LocalPlayer clientPlayer = Minecraft.getInstance().player;
        if (clientPlayer == null) {
            return;
        }

        final TrainProperties trainProperties = TrainClientRegistry.getTrainProperties(trainId);
        
        vehicleRidingClient.renderPlayers();

        doorOpening = doorValue > oldDoorValue;
        
        double railProgress = this.railProgress - 10.0;
        double bogiePosition = getBogiePosition() == 0 ? spacing / 2.0 : getBogiePosition();
        double bogieFOffset = (ridingCar * spacing + spacing / 2.0) - bogiePosition;
        double bogieBOffset = (ridingCar * spacing + spacing / 2.0) + bogiePosition;
        if (getIsJacobsBogie() && ridingCar != 0) bogieFOffset = (ridingCar * spacing + spacing / 2.0) - spacing / 2.0;
        if (getIsJacobsBogie() && ridingCar != trainCars - 1) bogieBOffset = (ridingCar * spacing + spacing / 2.0) + spacing / 2.0;
        
        float trackRoll = TrainExtraSupplier.getRollAngleAt((Train) (Object) this, ridingCar);
        
        if (isReversed()) {
            trackRoll = -trackRoll;
        }
        
        final float irregRatio = ClientConfig.hideRidingTrain ? 0 : Mth.clamp(speed / (5.56f * 0.05f), 0, 1);
        float noiseRoll = (float)(irregR.getAt(railProgress - bogieFOffset) + irregR.getAt(railProgress - bogieBOffset)) / 2 * irregRatio;
        
        float customRoll = trackRoll + noiseRoll;
        
        trainRenderer.renderCar(ridingCar, carX, carY, carZ, carYaw, carPitch, customRoll, doorLeftOpen, doorRightOpen);
        trainTranslucentRenders.add(() -> trainRenderer.renderCar(ridingCar, carX, carY, carZ, carYaw, carPitch, customRoll, doorLeftOpen, doorRightOpen));

        if (ridingCar > 0) {
            float prevTrackRoll = TrainExtraSupplier.getRollAngleAt((Train) (Object) this, ridingCar - 1);
            float currTrackRoll = TrainExtraSupplier.getRollAngleAt((Train) (Object) this, ridingCar);
            
            if (isReversed()) {
                prevTrackRoll = -prevTrackRoll;
                currTrackRoll = -currTrackRoll;
            }
            
            Matrices pre = new Matrices();
            pre.translate(prevCarX, prevCarY, prevCarZ);
            pre.rotateY(prevCarYaw);
            pre.rotateX(-prevCarPitch);
            pre.translate(0, -1, 0);
            pre.rotateZ(-prevTrackRoll);
            pre.translate(0, 1, 0);

            Matrices thi = new Matrices();
            thi.translate(carX, carY, carZ);
            thi.rotateY(carYaw);
            thi.rotateX(-carPitch);
            thi.translate(0, -1, 0);
            thi.rotateZ(-currTrackRoll);
            thi.translate(0, 1, 0);

            pre.pushPose();
            pre.translate(0, 0, spacing / 2D - 1);
            Vec3 prevPos0 = now(pre);
            pre.popPose();

            thi.pushPose();
            thi.translate(0, 0, -(spacing / 2D - 1));
            Vec3 thisPos0 = now(thi);
            thi.popPose();

            final Vec3 connectPos = prevPos0.add(thisPos0).scale(0.5);
            final float connectYaw = (float) Mth.atan2(thisPos0.x - prevPos0.x, thisPos0.z - prevPos0.z);
            final float connectPitch = realSpacing == 0 ? 0 : (float) Math.asin((thisPos0.y - prevPos0.y) / realSpacing);
            final float connectRoll = (currTrackRoll + prevTrackRoll) / 2;

            for (int i = 0; i < 2; i++) {
                final double xStart = width / 2D + (i == 0 ? -1 : 0.5) * CONNECTION_X_OFFSET;
                final double zStart = spacing / 2D - (i == 0 ? 1 : 2) * CONNECTION_Z_OFFSET;

                pre.pushPose();
                pre.translate(xStart, SMALL_OFFSET, zStart);
                Vec3 prevPos1 = now(pre);
                pre.translate(0, CONNECTION_HEIGHT, 0);
                Vec3 prevPos2 = now(pre);
                pre.popPose();

                pre.pushPose();
                pre.translate(-xStart, CONNECTION_HEIGHT + SMALL_OFFSET, zStart);
                Vec3 prevPos3 = now(pre);
                pre.translate(0, -CONNECTION_HEIGHT, 0);
                Vec3 prevPos4 = now(pre);
                pre.popPose();

                thi.pushPose();
                thi.translate(-xStart, SMALL_OFFSET, -zStart);
                Vec3 thisPos1 = now(thi);
                thi.translate(0, CONNECTION_HEIGHT, 0);
                Vec3 thisPos2 = now(thi);
                thi.popPose();

                thi.pushPose();
                thi.translate(xStart, CONNECTION_HEIGHT + SMALL_OFFSET, -zStart);
                Vec3 thisPos3 = now(thi);
                thi.translate(0, -CONNECTION_HEIGHT, 0);
                Vec3 thisPos4 = now(thi);
                thi.popPose();

                if (i == 0) {
                    trainRenderer.renderConnection(prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4, connectPos.x, connectPos.y, connectPos.z, connectYaw, connectPitch, connectRoll);
                } else {
                    trainRenderer.renderBarrier(prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4, connectPos.x, connectPos.y, connectPos.z, connectYaw, connectPitch, connectRoll);
                }
            }
        }
        
        ci.cancel();
    }

    @Shadow(remap = false) boolean isRemoved = false;
    @Shadow(remap = false) boolean justMounted;
    @Shadow(remap = false) float oldSpeed;
    @Shadow(remap = false) double oldRailProgress;
    @Shadow(remap = false) float oldDoorValue;
    @Shadow(remap = false) boolean doorOpening;
    @Shadow(remap = false) boolean isSitting;
    @Shadow(remap = false) boolean previousShifting;

    @Shadow(remap = false) static float CONNECTION_HEIGHT;
    @Shadow(remap = false) static float CONNECTION_Z_OFFSET;
    @Shadow(remap = false) static float CONNECTION_X_OFFSET;
    @Shadow(remap = false) TrainSoundBase trainSound;
    @Shadow(remap = false) VehicleRidingClient vehicleRidingClient;
    @Shadow(remap = false) TrainRendererBase trainRenderer;
    @Shadow(remap = false) Set<Runnable> trainTranslucentRenders;
    @Shadow(remap = false) SpeedCallback speedCallback;
    @Shadow(remap = false) AnnouncementCallback announcementCallback;
    @Shadow(remap = false) AnnouncementCallback lightRailAnnouncementCallback;
    @Shadow(remap = false) Depot depot;
    @Shadow(remap = false) List<Long> routeIds;

    @Shadow(remap = false) abstract int getPreviousStoppingIndex(int headIndex);
    @Shadow(remap = false) abstract boolean justOpening();

    // @Override
    protected boolean handlePositions123(Level world, Vec3[] positions, float ticksElapsed) {
        final Minecraft client = Minecraft.getInstance();
        final LocalPlayer clientPlayer = client.player;
        if (clientPlayer == null) {
            return false;
        }

        vehicleRidingClient.begin();

        if (ticksElapsed > 0) {
            if (isPlayerRiding(clientPlayer)) {
                final int headIndex = getIndex(0, spacing, false);
                final int stopIndex = path.get(headIndex).stopIndex - 1;

                if (speedCallback != null) {
                    speedCallback.speedCallback(speed * 20, stopIndex, routeIds);
                }

                if (announcementCallback != null) {
                    final double targetProgress = distances.get(getPreviousStoppingIndex(headIndex)) + (trainCars + 1) * spacing;
                    if (oldRailProgress < targetProgress && railProgress >= targetProgress) {
                        announcementCallback.announcementCallback(stopIndex, routeIds);
                    }
                }

                if (lightRailAnnouncementCallback != null && (justOpening() || justMounted)) {
                    lightRailAnnouncementCallback.announcementCallback(stopIndex, routeIds);
                }
            }

            final TrainProperties trainProperties = TrainClientRegistry.getTrainProperties(trainId);
            vehicleRidingClient.movePlayer(uuid -> {
                final CalculateCarCallback calculateCarCallback = (x, y, z, yaw, pitch, roll, realSpacingRender, doorLeftOpenRender, doorRightOpenRender) -> {                    
                    vehicleRidingClient.setOffsets(uuid, x, y, z, yaw, pitch, transportMode.maxLength == 1 ? spacing : realSpacingRender, width, doorLeftOpenRender, doorRightOpenRender, transportMode.hasPitchAscending, transportMode.hasPitchDescending, trainProperties.riderOffset, trainProperties.riderOffsetDismounting, speed > 0, doorValue == 0, () -> {
                        final boolean isShifting = clientPlayer.isShiftKeyDown();
                        if (Config.shiftToToggleSitting() && !MTRClient.isVivecraft()) {
                            if (isShifting && !previousShifting) {
                                isSitting = !isSitting;
                            }
                            clientPlayer.setPose(isSitting && !client.gameRenderer.getMainCamera().isDetached() ? Pose.CROUCHING : Pose.STANDING);
                        }
                        previousShifting = isShifting;
                    });
                };

                final int currentRidingCar = Mth.clamp((int) Math.floor(vehicleRidingClient.getPercentageZ(uuid)), 0, positions.length - 2);
                calculateCar(world, positions, currentRidingCar, 0, true, (x, y, z, yaw, pitch, roll, realSpacingRender, doorLeftOpenRender, doorRightOpenRender) -> {
                    vehicleRidingClient.moveSelf(id, uuid, realSpacingRender, width, yaw, currentRidingCar, trainCars, doorLeftOpenRender, doorRightOpenRender, !trainProperties.hasGangwayConnection, ticksElapsed);

                    final int newRidingCar = Mth.clamp((int) Math.floor(vehicleRidingClient.getPercentageZ(uuid)), 0, positions.length - 2);
                    if (currentRidingCar == newRidingCar) {
                        calculateCarCallback.calculateCarCallback(x, y, z, yaw, pitch, roll, realSpacingRender, doorLeftOpenRender, doorRightOpenRender);
                    } else {
                        calculateCar(world, positions, newRidingCar, 0, true, calculateCarCallback);
                    }
                });
            });
        }

        vehicleRidingClient.end();
        justMounted = false;

        final Entity camera = client.cameraEntity;
        final Vec3 cameraPos = camera == null ? Vec3.ZERO : camera.position();
        double nearestDistance = Double.POSITIVE_INFINITY;
        int nearestCar = 0;
        for (int i = 0; i < trainCars; i++) {
            final double checkDistance = cameraPos.distanceToSqr(positions[i]);
            if (checkDistance < nearestDistance) {
                nearestCar = i;
                nearestDistance = checkDistance;
            }
        }
        final BlockPos soundPos = RailwayData.newBlockPos(positions[nearestCar].x, positions[nearestCar].y, positions[nearestCar].z);
        trainSound.playNearestCar(world, soundPos, nearestCar);

        return true;
    }

    protected void simulateCar(
            Level world, int ridingCar, float ticksElapsed,
            double carX, double carY, double carZ, float carYaw, float carPitch, float carRoll,
            double prevCarX, double prevCarY, double prevCarZ, float prevCarYaw, float prevCarPitch, float prevCarRoll,
            boolean doorLeftOpen, boolean doorRightOpen, double realSpacing
    ) {
        final LocalPlayer clientPlayer = Minecraft.getInstance().player;
        if (clientPlayer == null) {
            return;
        }

        final BlockPos soundPos = RailwayData.newBlockPos(carX, carY, carZ);
        trainSound.playAllCars(world, soundPos, ridingCar);
        if (doorLeftOpen || doorRightOpen) {
            trainSound.playAllCarsDoorOpening(world, soundPos, ridingCar);
        }

        doorOpening = doorValue > oldDoorValue;
    }

    private static Vec3 now(Matrices matrices) {
        return matrices.last().getTranslationPart().toVec3();
    }
}
