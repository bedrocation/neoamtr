package cn.zbx1425.mtrsteamloco.gui;

import cn.zbx1425.mtrsteamloco.Main;
import cn.zbx1425.mtrsteamloco.block.BlockEyeCandy;
import cn.zbx1425.mtrsteamloco.block.BlockEyeCandy.BlockEntityEyeCandy;
import cn.zbx1425.mtrsteamloco.data.EyeCandyProperties;
import cn.zbx1425.mtrsteamloco.data.EyeCandyRegistry;
import cn.zbx1425.mtrsteamloco.network.PacketUpdateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import mtr.client.IDrawing;
import mtr.mappings.Text;
import mtr.mappings.UtilitiesClient;
import mtr.screen.WidgetBetterCheckbox;
import mtr.screen.WidgetBetterTextField;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
#if MC_VERSION >= "12000"
import net.minecraft.client.gui.GuiGraphics;
#endif
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import cn.zbx1425.mtrsteamloco.render.scripting.eyecandy.EyeCandyScriptContext;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.StringListEntry;
import me.shedaniel.clothconfig2.gui.entries.FloatListEntry;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.gui.screens.Screen;
import cn.zbx1425.mtrsteamloco.Main;
import net.minecraft.network.chat.Component;
import cn.zbx1425.mtrsteamloco.ClientConfig;
import me.shedaniel.clothconfig2.gui.entries.*;
import net.minecraft.client.gui.components.Button;
import cn.zbx1425.mtrsteamloco.gui.entries.*;
import com.mojang.blaze3d.platform.Window;
import cn.zbx1425.mtrsteamloco.gui.entries.SliderOrTextFieldListEntry;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.Function;

public class EyeCandyScreen {

    public static Screen createScreen(BlockPos blockPos, Screen parent) {
        Optional<BlockEntityEyeCandy> opt = getBlockEntity(blockPos);
        BlockEntityEyeCandy blockEntity = opt.orElse(null);
        if (blockEntity == null) {
            return null;
        }

        List<Consumer<BlockEntityEyeCandy>> update = new ArrayList<>();// updateBlockEntityCallbacks;

        EyeCandyProperties properties = blockEntity.getProperties();
        String pid = "";
        if (properties != null) {
            pid = properties.name.getString() + " (" + blockEntity.prefabId + ")";
        }

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(tr("title"))
                .setDoesConfirmSave(false)
                .setSavingRunnable(() -> {
                    for (Consumer<BlockEntityEyeCandy> callback : update) {
                        callback.accept(blockEntity);
                    }
                    blockEntity.sendUpdateC2S();
                })
                .transparentBackground();
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory common = builder.getOrCreateCategory(
                Text.translatable("gui.mtrsteamloco.config.client.category.common")
        );

        common.addEntry(new ButtonListEntry(
                Text.literal(""),
                #if MC_VERSION >= "11903"
        new Button.Builder(
                Text.translatable("gui.mtrsteamloco.eye_candy.present",
                        (properties != null ? (properties.name.getString() + " (" + blockEntity.prefabId + ")") : (blockEntity.prefabId + " (???)"))),
                btn -> Minecraft.getInstance().setScreen(new SelectScreen(blockPos))).pos(0, 0).size(300, 20).build(),
#else
        new Button(0, 0, 300, 20,
                Text.translatable("gui.mtrsteamloco.eye_candy.present",
                        (properties != null ? (properties.name.getString() + " (" + blockEntity.prefabId + ")") : (blockEntity.prefabId + " (???)"))),
                btn -> Minecraft.getInstance().setScreen(new SelectScreen(blockPos))),
#endif
                (e, b, a1, a2, a3, a4, a5, a6, a7, a8, a9) -> {
            Window window = Minecraft.getInstance().getWindow();
#if MC_VERSION >= "11903"
            b.setX(window.getGuiScaledWidth() / 2 - 150);
#else
            b.x = window.getGuiScaledWidth() / 2 - 150;
#endif
        }));

        common.addEntry(entryBuilder
                .startBooleanToggle(
                        tr("full_light"),
                        blockEntity.fullLight
                ).setSaveConsumer(checked -> {
                    if (checked != blockEntity.fullLight) {
                        update.add(be -> be.fullLight = checked);
                    }
                }).setDefaultValue(blockEntity.fullLight).build()
        );

        common.addEntry(entryBuilder
                .startBooleanToggle(
                        tr("as_platform"),
                        blockEntity.asPlatform
                ).setSaveConsumer(checked -> {
                    if (checked != blockEntity.asPlatform) {
                        update.add(be -> be.asPlatform = checked);
                    }
                }).setDefaultValue(blockEntity.asPlatform).build()
        );

        common.addEntry(entryBuilder.startTextDescription(
                Text.translatable("gui.mtrsteamloco.eye_candy.shape", blockEntity.getShape())
        ).build());

        common.addEntry(entryBuilder.startTextDescription(
                Text.translatable("gui.mtrsteamloco.eye_candy.collision", blockEntity.getCollisionShape())
        ).build());

        if (blockEntity.fixedMatrix) {
            common.addEntry(entryBuilder.startTextDescription(
                    tr("fixed")
            ).build());

            common.addEntry(entryBuilder.startTextDescription(
                    Text.literal("TX: " + blockEntity.translateX * 100 + "cm, TY: " + blockEntity.translateY * 100 + "cm, TZ: " + blockEntity.translateZ * 100 + "cm")
            ).build());

            common.addEntry(entryBuilder.startTextDescription(
                    Text.literal("RX: " + Math.toDegrees(blockEntity.rotateX) + "°, RY: " + Math.toDegrees(blockEntity.rotateY) + "°, RZ: " + Math.toDegrees(blockEntity.rotateZ) + "°")
            ).build());

            common.addEntry(entryBuilder.startTextDescription(
                    Text.literal("SX: " + blockEntity.scaleX + ", SY: " + blockEntity.scaleY + ", SZ: " + blockEntity.scaleZ)
            ).build());
        } else {
            List<SliderOrTextFieldListEntry> entries = new ArrayList<>();

            common.addEntry(new SimpleButtonListEntry(
                    tr("adjust_settings_and_reset_pose"),
                    Text.translatable("gui.mtrsteamloco.adjust_settings"),
                    btn -> Minecraft.getInstance().setScreen(createAdjustScreen(blockPos, () -> createScreen(blockPos, parent))),
                    entryBuilder.getResetButtonKey(),
                    btn -> {
                        blockEntity.translateX = 0;
                        blockEntity.translateY = 0;
                        blockEntity.translateZ = 0;
                        blockEntity.rotateX = 0;
                        blockEntity.rotateY = 0;
                        blockEntity.rotateZ = 0;
                        blockEntity.scaleX = 1;
                        blockEntity.scaleY = 1;
                        blockEntity.scaleZ = 1;
                        blockEntity.sendUpdateC2S();
                        for (int i = 0; i < 9; i++) {
                            entries.get(i).setValue(getValue(i, blockEntity));
                        }
                    },
                    entryBuilder.getResetButtonKey()
            ));

            addTranslation(entries, common, entryBuilder, 0, blockEntity);
            addTranslation(entries, common, entryBuilder, 1, blockEntity);
            addTranslation(entries, common, entryBuilder, 2, blockEntity);
            addRotation(entries, common, entryBuilder, 3, blockEntity);
            addRotation(entries, common, entryBuilder, 4, blockEntity);
            addRotation(entries, common, entryBuilder, 5, blockEntity);
            addScale(entries, common, entryBuilder, 6, blockEntity);
            addScale(entries, common, entryBuilder, 7, blockEntity);
            addScale(entries, common, entryBuilder, 8, blockEntity);
        }

        List<AbstractConfigListEntry> customEntrys = blockEntity.getCustomConfigEntrys(entryBuilder, () -> createScreen(blockPos, parent));
        for (AbstractConfigListEntry entry : customEntrys) {
            common.addEntry(entry);
        }

        return builder.build();
    }

    private static Screen createAdjustScreen(BlockPos blockPos, Supplier<Screen> parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(new FakeScreen(parent))
                .setTitle(Text.translatable("gui.mtrsteamloco.adjust_settings"))
                .setDoesConfirmSave(false)
                .setSavingRunnable(() -> {
                    ClientConfig.save();
                })
                .transparentBackground();
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory common = builder.getOrCreateCategory(
                Text.translatable("gui.mtrsteamloco.config.client.category.common")
        );

        List<AbstractConfigListEntry> entries = new ArrayList<>();
        ClientConfig.eyecandyScreenGroup.getListEntries(entries, entryBuilder, () -> createAdjustScreen(blockPos, parent));

        for (AbstractConfigListEntry entry : entries) {
            common.addEntry(entry);
        }

        return builder.build();
    }

    private static void save(int type, float value, BlockEntityEyeCandy blockEntity) {
        float old = getValue(type, blockEntity);
        if (old == value) return;
        switch (type) {
            case 0: blockEntity.translateX = value; break;
            case 1: blockEntity.translateY = value; break;
            case 2: blockEntity.translateZ = value; break;
            case 3: blockEntity.rotateX = value; break;
            case 4: blockEntity.rotateY = value; break;
            case 5: blockEntity.rotateZ = value; break;
            case 6: blockEntity.scaleX = value; break;
            case 7: blockEntity.scaleY = value; break;
            case 8: blockEntity.scaleZ = value; break;
        }
        blockEntity.sendUpdateC2S();
    }

    private static float getValue(int type, BlockEntityEyeCandy blockEntity) {
        switch (type) {
            case 0: return blockEntity.translateX;
            case 1: return blockEntity.translateY;
            case 2: return blockEntity.translateZ;
            case 3: return blockEntity.rotateX;
            case 4: return blockEntity.rotateY;
            case 5: return blockEntity.rotateZ;
            case 6: return blockEntity.scaleX;
            case 7: return blockEntity.scaleY;
            case 8: return blockEntity.scaleZ;
        }
        return 0;
    }

    private static String getStr(int type, BlockEntityEyeCandy blockEntity) {
        switch (type) {
            case 0: return "TX";
            case 1: return "TY";
            case 2: return "TZ";
            case 3: return "RX";
            case 4: return "RY";
            case 5: return "RZ";
            case 6: return "SX";
            case 7: return "SY";
            case 8: return "SZ";
        }
        return "";
    }

    private static void addTranslation(List<SliderOrTextFieldListEntry> entries, ConfigCategory common, ConfigEntryBuilder entryBuilder, int type, BlockEntityEyeCandy blockEntity) {
        ClientConfig.Entry e = ClientConfig.eyecandyScreenGroup.entries[0];
        int type0 = type;
        SliderOrTextFieldListEntry entry = new SliderOrTextFieldListEntry(
                Text.literal(getStr(type, blockEntity)),
                entryBuilder.getResetButtonKey(),
                getValue(type, blockEntity),
                e.min, e.max, e.step, f -> String.format("%.0fCM", f * 100),
                f -> save(type, f, blockEntity),
                str -> parseMovement(str),
                e.modes[type0], i -> {
            e.modes[type0] = i;
            ClientConfig.save();
        }
        );

        common.addEntry(entry);
        entries.add(entry);
    }

    private static void addRotation(List<SliderOrTextFieldListEntry> entries, ConfigCategory common, ConfigEntryBuilder entryBuilder, int type, BlockEntityEyeCandy blockEntity) {
        ClientConfig.Entry e = ClientConfig.eyecandyScreenGroup.entries[1];
        int type0 = type - 3;
        SliderOrTextFieldListEntry entry = new SliderOrTextFieldListEntry(
                Text.literal(getStr(type, blockEntity)),
                entryBuilder.getResetButtonKey(),
                getValue(type, blockEntity) * 180 / (float) Math.PI,
                e.min, e.max, e.step, f -> String.format("%.0f°", f),
                f -> save(type, f / 180 * (float) Math.PI, blockEntity),
                str -> parseRotation(str),
                e.modes[type0], i -> {
            e.modes[type0] = i;
            ClientConfig.save();
        }
        );

        common.addEntry(entry);
        entries.add(entry);
    }

    private static void addScale(List<SliderOrTextFieldListEntry> entries, ConfigCategory common, ConfigEntryBuilder entryBuilder, int type, BlockEntityEyeCandy blockEntity) {
        ClientConfig.Entry e = ClientConfig.eyecandyScreenGroup.entries[2];
        int type0 = type - 6;
        SliderOrTextFieldListEntry entry = new SliderOrTextFieldListEntry(
                Text.literal(getStr(type, blockEntity)),
                entryBuilder.getResetButtonKey(),
                getValue(type, blockEntity),
                e.min, e.max, e.step, f -> String.format("%.2f", f),
                f -> save(type, f, blockEntity),
                str -> parseScale(str),
                e.modes[type0], i -> {
            e.modes[type0] = i;
            ClientConfig.save();
        }
        );

        common.addEntry(entry);
        entries.add(entry);
    }

    private static Optional<Float> parseScale(String str) {
        try {
            Float value = Float.parseFloat(str);
            return Optional.of(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Float> parseMovement(String str) {
        try {
            Float value = 0f;
            str = str.toLowerCase().trim();
            if (str.endsWith("cm")) {
                value = Float.parseFloat(str.substring(0, str.length() - 2)) / 100;
            } else if (str.endsWith("m")) {
                value = Float.parseFloat(str.substring(0, str.length() - 1));
            } else {
                value = Float.parseFloat(str);
            }
            return Optional.of(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Float> parseRotation(String str) {
        try {
            Float value = 0f;
            str = str.toLowerCase().trim();
            if (str.endsWith("°")) {
                value = Float.parseFloat(str.substring(0, str.length() - 1));
            } else {
                value = Float.parseFloat(str);
            }
            return Optional.of(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<BlockEyeCandy.BlockEntityEyeCandy> getBlockEntity(BlockPos blockPos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return Optional.empty();
        return level.getBlockEntity(blockPos, Main.BLOCK_ENTITY_TYPE_EYE_CANDY.get());
    }

    private static Component tr(String key) {
        return Text.translatable("gui.mtrsteamloco.eye_candy." + key);
    }

    private static class SelectScreen extends SelectListScreen {
        private static final String INSTRUCTION_LINK = "https://aphrodite281.github.io/mtr-ante/#/eyecandy";
        private final WidgetLabel lblInstruction = new WidgetLabel(0, 0, 0, Text.translatable("gui.mtrsteamloco.eye_candy.tip_resource_pack"), () -> {
            this.minecraft.setScreen(new ConfirmLinkScreen(bl -> {
                if (bl) {
                    Util.getPlatform().openUri(INSTRUCTION_LINK);
                }
                this.minecraft.setScreen(this);
            }, INSTRUCTION_LINK, true));
        });

        private final BlockPos editingBlockPos;
        private final List<Pair<String, String>> pairs = new ArrayList<>();
        private final Map<String, String> nameMap = new HashMap<>();

        public SelectScreen(BlockPos blockPos) {
            super(Text.literal("Select EyeCandy"));
            this.editingBlockPos = blockPos;
            Set<Map.Entry<String, EyeCandyProperties>> entries = EyeCandyRegistry.elements.entrySet();

            for (Map.Entry<String, EyeCandyProperties> entry : entries) {
                EyeCandyProperties prop = entry.getValue();
                String prid = entry.getKey();
                String name = prop.name.getString();
                pairs.add(new Pair<>(prid, name + " (" + prid + ")"));
                nameMap.put(name + " (" + prid + ")", prid);
            }
        }

        @Override
        protected void init() {
            super.init();

            loadPage();
        }

        @Override
#if MC_VERSION >= "12000"
        public void render(@NotNull GuiGraphics guiGraphics, int i, int j, float f) {
#else
            public void render(@NotNull PoseStack guiGraphics, int i, int j, float f) {
#endif
                this.renderBackground(guiGraphics, i, j, f);
                super.render(guiGraphics, i, j, f);
                super.renderSelectPage(guiGraphics);
            }

            @Override
            protected void loadPage() {
                clearWidgets();

                Optional<BlockEyeCandy.BlockEntityEyeCandy> optionalBlockEntity = getBlockEntity();
                if (optionalBlockEntity.isEmpty()) { this.onClose(); return; }
                BlockEyeCandy.BlockEntityEyeCandy blockEntity = optionalBlockEntity.get();
                scrollList.visible = true;
                loadSelectPage(key -> !key.equals(blockEntity.prefabId));
                lblInstruction.alignR = true;
                IDrawing.setPositionAndWidth(lblInstruction, width / 2 + SQUARE_SIZE, height - SQUARE_SIZE - TEXT_HEIGHT, 0);
                lblInstruction.setWidth(width / 2 - SQUARE_SIZE * 2);
                addRenderableWidget(lblInstruction);
            }

            @Override
            protected void onBtnClick(String btnKey) {
                updateBlockEntity(blockEntity -> blockEntity.setPrefabId(btnKey));
            }

            @Override
            protected List<Pair<String, String>> getRegistryEntries() {
                return pairs;
            }

            private void updateBlockEntity(Consumer<BlockEyeCandy.BlockEntityEyeCandy> modifier) {
                getBlockEntity().ifPresent(blockEntity -> {
                    modifier.accept(blockEntity);
                    PacketUpdateBlockEntity.sendUpdateC2S(blockEntity);
                });
            }

            private Optional<BlockEyeCandy.BlockEntityEyeCandy> getBlockEntity() {
                Level level = Minecraft.getInstance().level;
                if (level == null) return Optional.empty();
                return level.getBlockEntity(editingBlockPos, Main.BLOCK_ENTITY_TYPE_EYE_CANDY.get());
            }

            @Override
            public void onClose() {
                this.minecraft.setScreen(EyeCandyScreen.createScreen(editingBlockPos, null));
            }

            @Override
            public boolean isPauseScreen() {
                return false;
            }
        }
    }
