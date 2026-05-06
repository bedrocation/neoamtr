package cn.zbx1425.mtrsteamloco.mixin;

import mtr.client.TrainClientRegistry;
import mtr.client.TrainProperties;
import mtr.mappings.Text;
import mtr.render.JonModelTrainRenderer;
import mtr.render.TrainRendererBase;
import mtr.sound.JonTrainSound;
import mtr.sound.TrainSoundBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrainClientRegistry.class)
public class TrainClientRegistryMixin {

    @Unique
    private static TrainProperties DUMMY_PROPERTY = null;

    @Unique
    private static TrainProperties getDummyProperty() {
        if (DUMMY_PROPERTY == null) {
            DUMMY_PROPERTY = new TrainProperties(
                    "train_1_1", Text.translatable(""), null, null, 0, 0.0F, 0.0F, -1.0F, 0.0F, false, false,
                    new JonModelTrainRenderer(null, "", "", ""),
                    new JonTrainSound("", new JonTrainSound.JonTrainSoundConfig(null, 0, 0.5F, false, false))
            );
        }
        return DUMMY_PROPERTY;
    }

    @Inject(method = "getTrainProperties(Ljava/lang/String;)Lmtr/client/TrainProperties;", at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void getTrainProperties(String key, CallbackInfoReturnable<TrainProperties> cir) {
        if ("$NTE_DUMMY_BLANK_PROPERTY".equals(key)) {
            cir.setReturnValue(getDummyProperty());
        }
    }
}