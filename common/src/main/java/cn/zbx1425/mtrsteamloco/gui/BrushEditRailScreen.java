package cn.zbx1425.mtrsteamloco.gui;

import cn.zbx1425.mtrsteamloco.Main;
import cn.zbx1425.mtrsteamloco.data.*;
import cn.zbx1425.mtrsteamloco.gui.entries.ButtonListEntry;
import cn.zbx1425.mtrsteamloco.network.PacketUpdateHoldingItem;
import cn.zbx1425.mtrsteamloco.network.PacketUpdateRail;
import cn.zbx1425.mtrsteamloco.render.RailPicker;
import mtr.client.ClientData;
import mtr.client.IDrawing;
import mtr.data.Rail;
import mtr.data.RailType;
import mtr.data.TransportMode;
import mtr.mappings.Text;
import mtr.mappings.UtilitiesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import cn.zbx1425.mtrsteamloco.block.BlockDirectNode.BlockEntityDirectNode;
import me.shedaniel.clothconfig2.api.*;
import me.shedaniel.clothconfig2.gui.entries.*;
import mtr.block.BlockNode;
import mtr.screen.WidgetBetterTextField;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.mtrsteamloco.network.util.DoubleFloatMapSerializer;
import cn.zbx1425.mtrsteamloco.gui.entries.*;
import cn.zbx1425.mtrsteamloco.network.PacketReplaceRailNode;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class BrushEditRailScreen {

    private final Rail pickedRail;
    private final RailExtraSupplier supplier;
    private final BlockPos pickedPosStart;
    private final BlockPos pickedPosEnd;

    private Screen screen;
    private Screen parent;

    private BrushEditRailScreen(Rail pickedRail, BlockPos pickedPosStart, BlockPos pickedPosEnd, Screen parent) {
        if (pickedRail.railType == RailType.NONE) {
            pickedRail = null;
            Map<BlockPos, Rail> mp0 = ClientData.RAILS.get(pickedPosEnd);
            if (mp0 != null) pickedRail = mp0.get(pickedPosStart);
            BlockPos temp = pickedPosStart;
            pickedPosStart = pickedPosEnd;
            pickedPosEnd = temp;
        }
        this.pickedRail = pickedRail;
        this.supplier = (RailExtraSupplier) pickedRail;
        this.pickedPosStart = pickedPosStart;
        this.pickedPosEnd = pickedPosEnd;
        this.parent = parent;
        init();
    }

    private BrushEditRailScreen(Screen parent) {
        this(RailPicker.pickedRail, RailPicker.pickedPosStart, RailPicker.pickedPosEnd, parent);
    }

    private void init() {
        if (pickedRail == null) screen = parent;
        else {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("gui.mtrsteamloco.brush_edit_rail.title"))
                    .setDoesConfirmSave(false)
                    .transparentBackground();
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();
            ConfigCategory common = builder.getOrCreateCategory(
                    Text.translatable("gui.mtrsteamloco.config.client.category.common")
            );

            CompoundTag brushTag = getBrushTag();
            String modelKey = supplier.getModelKey();

            Minecraft minecraft = Minecraft.getInstance();
            BlockEntityDirectNode entity = null;
            if (minecraft != null) {
                if (minecraft.level != null) {
                    BlockEntity e = minecraft.level.getBlockEntity(pickedPosStart);
                    if (e != null) {
                        if (e instanceof BlockEntityDirectNode en) {
                            entity = en;
                        }
                    }
                }
            }

            if (entity != null) {
                final BlockEntityDirectNode entity1 = entity;
                common.addEntry(
                    ButtonListEntry.createCenteredInstance(
                        Text.translatable("gui.mtrsteamloco.brush_edit_rail.adjust_angle"),
                        btn -> minecraft.setScreen(DirectNodeScreen.createScreen(entity1, () -> createScreen(pickedRail, pickedPosStart, pickedPosEnd, parent)))
                    )
                );
            } else if (pickedRail.transportMode == TransportMode.TRAIN) {
                BlockState state = minecraft.level.getBlockState(pickedPosStart);
                if (state != null && state.getBlock() instanceof BlockNode) {
                    common.addEntry(
                        ButtonListEntry.createCenteredInstance(
                            Text.translatable("gui.mtrsteamloco.brush_edit_rail.switch_to_direct_node"),
                            btn -> PacketReplaceRailNode.sendUpdateC2S(pickedPosStart, state, "brush_edit_rail")
                        )
                    );
                }
            }


            common.addEntry(
                entryBuilder.startTextDescription(
                    Text.translatable("gui.mtrsteamloco.brush_edit_rail.brush_hint")
                ).build());

            boolean enableRailType = brushTag != null && brushTag.contains("RailType");
            common.addEntry(
                entryBuilder.startBooleanToggle(
                    Text.translatable("gui.mtrsteamloco.brush_edit_rail.enable_rail_type"),
                    enableRailType
                ).setTooltipSupplier(checked -> {
                    if (checked != enableRailType) {
                        updateBrushTag(
                            compoundTag -> {
                                if (checked) {
                                    compoundTag.putString("RailType", pickedRail.railType.toString());
                                } else {
                                    compoundTag.remove("RailType");
                                }
                            }
                        );
                        Minecraft.getInstance().setScreen(BrushEditRailScreen.createScreen(pickedRail, pickedPosStart, pickedPosEnd, parent));
                    }
                    return Optional.empty();
                }).build()
            );
            if (enableRailType) {
                common.addEntry(
                    entryBuilder.startTextField(
                        Text.translatable("gui.mtrsteamloco.brush_edit_rail.rail_type"),
                        brushTag.getString("RailType")
                    ).setErrorSupplier(str -> {
                        try {
                            RailType type = RailType.valueOf(str);
                            if (type != null && type != RailType.NONE) return Optional.empty();
                        } catch (Exception e) {}
                        return Optional.of(Text.translatable("gui.mtrsteamloco.brush_edit_rail.rail_type_error"));
                    }
                    ).setSaveConsumer(str -> {
                        updateBrushTag(compoundTag -> {
                            compoundTag.putString("RailType", str);
                        });
                    }).build()
                );
            }

            boolean enableModelKey = brushTag != null && brushTag.contains("ModelKey");
            common.addEntry(
                entryBuilder.startBooleanToggle(
                    Text.translatable("gui.mtrsteamloco.brush_edit_rail.enable_model_key"),
                    enableModelKey
                ).setTooltipSupplier(checked -> {
                    if (checked != enableModelKey) {
                        updateBrushTag(
                            compoundTag -> {
                                if (checked) {
                                    compoundTag.putString("ModelKey", modelKey);
                                } else {
                                    compoundTag.remove("ModelKey");
                                }
                            });
                        Minecraft.getInstance().setScreen(BrushEditRailScreen.createScreen(pickedRail, pickedPosStart, pickedPosEnd, parent));
                    }
                    return Optional.empty();
                })
                .setDefaultValue(enableModelKey).build()
            );

            if (enableModelKey) {
                RailModelProperties properties = RailModelRegistry.elements.get(modelKey);
                common.addEntry(ButtonListEntry.createCenteredInstance(
                    Text.translatable("gui.mtrsteamloco.brush_edit_rail.present", (properties != null ? (properties.name.getString()) : (modelKey + " (???)"))),
                    btn -> Minecraft.getInstance().setScreen(new SelectScreen())
                ));
            }

            boolean enableVertCurveRadius = brushTag != null && brushTag.contains("VerticalCurveRadius");
            float vertCurveRadius = ((RailExtraSupplier)pickedRail).getVerticalCurveRadius();
            common.addEntry(
                entryBuilder.startBooleanToggle(
                    Text.translatable("gui.mtrsteamloco.brush_edit_rail.enable_vertical_curve_radius"),
                    enableVertCurveRadius
                ).setTooltipSupplier(checked -> {
                    if (checked != enableVertCurveRadius) {
                        updateBrushTag(compoundTag -> {
                            if (checked) {
                                compoundTag.putFloat("VerticalCurveRadius", vertCurveRadius);
                            } else {
                                compoundTag.remove("VerticalCurveRadius");
                            }
                        });
                        Minecraft.getInstance().setScreen(BrushEditRailScreen.createScreen(pickedRail, pickedPosStart, pickedPosEnd, parent));
                    }
                    return Optional.empty();
                })
                .setDefaultValue(enableVertCurveRadius).build()
            );
            if (enableVertCurveRadius) {
                common.addEntry(new VertCurveRadiusListEntry(vertCurveRadius, this));
            }

            boolean enableRollAngle = brushTag != null && brushTag.contains("RollAngleMap");
            common.addEntry(entryBuilder.startBooleanToggle(Text.translatable("gui.mtrsteamloco.brush_edit_rail.enable_roll_angle"), enableRollAngle)
                    .setTooltipSupplier(checked -> {
                        if (checked != enableRollAngle) {
                            updateBrushTag(compoundTag -> {
                                if (!checked) compoundTag.remove("RollAngleMap");
                                else compoundTag.putString("RollAngleMap", DoubleFloatMapSerializer.serializeToString(new HashMap<>()));
                            });
                            Minecraft.getInstance().setScreen(BrushEditRailScreen.createScreen(pickedRail, pickedPosStart, pickedPosEnd, parent));
                        }
                        return Optional.empty();
                    }).build());

            if (enableRollAngle) {
                Vector3f p1 = new Vector3f(pickedRail.getPosition(0));
                Vector3f start = new Vector3f(pickedPosStart);
                boolean flag = p1.distance(start) < new Vector3f(pickedRail.getPosition(pickedRail.getLength())).distance(start);
                common.addEntry(new RollAnglesListEntry(pickedRail, flag, this::updateRailAngle, f -> PacketUpdateRail.sendUpdateC2S(pickedRail, pickedPosStart, pickedPosEnd), () -> BrushEditRailScreen.createScreen(pickedRail, pickedPosStart, pickedPosEnd, parent)));
            }

            common.addEntry(entryBuilder.startIntSlider(Text.translatable("开门方向"), supplier.getOpeningDirection(), 0, 3)
                    .setTooltipSupplier(v -> Optional.of(new Component[]{Text.translatable("gui.mtrsteamloco.brush_edit_rail.opening_direction_tooltip")}))
                    .setDefaultValue(0)
                    .setSaveConsumer(value -> {
                        supplier.setOpeningDirection(value);
                        PacketUpdateRail.sendUpdateC2S(pickedRail, pickedPosStart, pickedPosEnd);
                    }).build());

            ConfigResponder.getEntrysFromMaps(supplier.getCustomConfigs(), supplier.getCustomResponders(), entryBuilder,
                    () -> BrushEditRailScreen.createScreen(pickedRail, pickedPosStart, pickedPosEnd, parent)).forEach(common::addEntry);

            screen = builder.build();
        }
    }

    public void updateRailAngle(Map<Double, Float> res) {
        PacketUpdateRail.sendUpdateC2S(pickedRail, pickedPosStart, pickedPosEnd);
        updateBrushTag(compoundTag -> {
            Map<Double, Float> res1 = new HashMap<>();
            double length = pickedRail.getLength();
            for (Map.Entry<Double, Float> entry : res.entrySet()) {
                res1.put(entry.getKey() / length, entry.getValue());
            }
            compoundTag.putString("RollAngleMap", DoubleFloatMapSerializer.serializeToString(res1));
        });
    }

    public void updateRadius(float newRadius, boolean send) {
        if (send) updateBrushTag(compoundTag -> compoundTag.putFloat("VerticalCurveRadius", newRadius));
    }

    public Component[] getVerticalValueText(float verticalRadius) {
        if (pickedRail == null) return new Component[] {Text.literal("(???)")};
        int H = Math.abs(((RailExtraSupplier)pickedRail).getHeight());
        double L = pickedRail.getLength();
        double maxRadius = (H == 0) ? 0 : (H * H + L * L) / (H * 4);
        double gradient = (verticalRadius < 0) ? H / L * 1000 : Math.tan(RailExtraSupplier.getVTheta(pickedRail, (verticalRadius == 0 || verticalRadius > maxRadius) ? maxRadius : verticalRadius)) * 1000;
        return new Component[] {
                Text.translatable("gui.mtrsteamloco.brush_edit_rail.vertical_curve_radius_values.1"),
                Text.translatable("gui.mtrsteamloco.brush_edit_rail.vertical_curve_radius_values.2", String.format("%.1f", maxRadius)),
                Text.translatable("gui.mtrsteamloco.brush_edit_rail.vertical_curve_radius_values.3", String.format("%.1f", gradient))};
    }

    public Screen getScreen() { return screen; }

    public static Screen createScreen(Screen parent) { return new BrushEditRailScreen(parent).getScreen(); }

    public static Screen createScreen(Rail rail, BlockPos posStart, BlockPos posEnd, Screen parent) {
        return new BrushEditRailScreen(rail, posStart, posEnd, parent).getScreen();
    }

    public static void applyBrushToPickedRail(BlockPos posStart, BlockPos posEnd, Rail pickedRail, CompoundTag railBrushProp, boolean isBatchApply) {
        if (railBrushProp == null || posStart == null || posEnd == null || pickedRail == null) return;
        if (pickedRail.railType == RailType.NONE) {
            Map<BlockPos, Rail> r1 = ClientData.RAILS.get(posEnd);
            if (r1 == null) return;
            pickedRail = r1.get(posStart);
            if (pickedRail == null || pickedRail.railType == RailType.NONE) return;
            BlockPos temp = posStart; posStart = posEnd; posEnd = temp;
        }
        RailExtraSupplier pickedExtra = (RailExtraSupplier) pickedRail;
        boolean propertyUpdated = false;
        if (railBrushProp.contains("ModelKey") && !railBrushProp.getString("ModelKey").equals(pickedExtra.getModelKey())) {
            pickedExtra.setModelKey(railBrushProp.getString("ModelKey"));
            propertyUpdated = true;
        }
        if (railBrushProp.contains("VerticalCurveRadius") && railBrushProp.getFloat("VerticalCurveRadius") != pickedExtra.getVerticalCurveRadius()) {
            pickedExtra.setVerticalCurveRadius(railBrushProp.getFloat("VerticalCurveRadius"));
            propertyUpdated = true;
        }
        if (railBrushProp.contains("RollAngleMap")) {
            Map<Double, Float> raw = DoubleFloatMapSerializer.deserialize(railBrushProp.getString("RollAngleMap"));
            Map<Double, Float> rollAngleMap = new HashMap<>();
            double length = pickedRail.getLength();
            for(Map.Entry<Double, Float> entry : raw.entrySet()) rollAngleMap.put(entry.getKey() * length, entry.getValue());
            pickedExtra.setRollAngleMap(rollAngleMap);
            propertyUpdated = true;
        }
        if (railBrushProp.contains("RailType")) {
            pickedExtra.setRailType(RailType.valueOf(railBrushProp.getString("RailType")));
            Main.LOGGER.info("RailType updated to " + railBrushProp.getString("RailType"));
            propertyUpdated = true;
        }
        if (isBatchApply && !propertyUpdated) pickedExtra.setRenderReversed(!pickedExtra.getRenderReversed());
        PacketUpdateRail.sendUpdateC2S(pickedRail, posStart, posEnd);
    }

    public static CompoundTag getBrushTag() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;
        ItemStack brushItem = minecraft.player.getMainHandItem();
        if (!brushItem.is(mtr.Items.BRUSH.get())) return null;

#if MC_VERSION >= "12005"
        net.minecraft.world.item.component.CustomData customData = brushItem.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag().getCompound("NTERailBrush") : null;
#else
        return brushItem.getTagElement("NTERailBrush");
#endif
    }

    public void updateBrushTag(Consumer<CompoundTag> modifier) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        ItemStack brushItem = minecraft.player.getMainHandItem();
        if (!brushItem.is(mtr.Items.BRUSH.get())) return;

#if MC_VERSION >= "12005"
        net.minecraft.world.item.component.CustomData customData = brushItem.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
        CompoundTag fullTag = customData.copyTag();
        CompoundTag nteTag = fullTag.getCompound("NTERailBrush");
        modifier.accept(nteTag);
        fullTag.put("NTERailBrush", nteTag);
        brushItem.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(fullTag));
#else
        CompoundTag nteTag = brushItem.getOrCreateTagElement("NTERailBrush");
        modifier.accept(nteTag);
#endif
        applyBrushToPickedRail(pickedPosStart, pickedPosEnd, pickedRail, getBrushTag(), false);
        PacketUpdateHoldingItem.sendUpdateC2S();
    }

    private class SelectScreen extends SelectListScreen {
        private static final String INSTRUCTION_LINK = "https://aphrodite281.github.io/mtr-ante/#/railmodel";
        private final WidgetLabel lblInstruction = new WidgetLabel(0, 0, 0, Text.translatable("gui.mtrsteamloco.eye_candy.tip_resource_pack"), () -> {
            this.minecraft.setScreen(new ConfirmLinkScreen(bl -> {
                if (bl) Util.getPlatform().openUri(INSTRUCTION_LINK);
                this.minecraft.setScreen(this);
            }, INSTRUCTION_LINK, true));
        });

        public SelectScreen() { super(Text.literal("Select rail arguments")); }

        @Override protected void init() { super.init(); loadPage(); }

        @Override protected void loadPage() {
            clearWidgets();
            CompoundTag brushTag = getBrushTag();
            String modelKey = brushTag == null ? "" : brushTag.getString("ModelKey");
            scrollList.visible = true;
            loadSelectPage(key -> !key.equals(modelKey));
            lblInstruction.alignR = true;
            IDrawing.setPositionAndWidth(lblInstruction, width / 2 + SQUARE_SIZE, height - SQUARE_SIZE - TEXT_HEIGHT, 0);
            lblInstruction.setWidth(width / 2 - SQUARE_SIZE * 2);
            addRenderableWidget(lblInstruction);
        }

        @Override protected void onBtnClick(String btnKey) {
            BrushEditRailScreen.this.updateBrushTag(compoundTag -> {
                compoundTag.putString("ModelKey", btnKey);
                applyBrushToPickedRail(pickedPosStart, pickedPosEnd, pickedRail, compoundTag, false);
            });
        }

        @Override protected List<Pair<String, String>> getRegistryEntries() {
            return RailModelRegistry.elements.entrySet().stream()
                    .filter(e -> !e.getValue().name.getString().isEmpty())
                    .map(e -> new Pair<>(e.getKey(), e.getValue().name.getString()))
                    .toList();
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            renderSelectPage(guiGraphics);
        }

        @Override public void onClose() { this.minecraft.setScreen(BrushEditRailScreen.createScreen(pickedRail, pickedPosStart, pickedPosEnd, parent)); }
        @Override public boolean isPauseScreen() { return true; }
    }

    @Environment(EnvType.CLIENT)
    private class VertCurveRadiusListEntry extends TooltipListEntry<String> implements ContainerEventHandler {
        private final Button btnSetDefaultRadius = UtilitiesClient.newButton(Text.translatable("gui.mtrsteamloco.brush_edit_rail.vertical_curve_radius_set_max"), sender -> updateRadius(0, true));
        private final Button btnSetNoRadius = UtilitiesClient.newButton(Text.translatable("gui.mtrsteamloco.brush_edit_rail.vertical_curve_radius_set_none"), sender -> updateRadius(-1, true));
        private final WidgetBetterTextField radiusInput = new WidgetBetterTextField("", 8);
        private final BrushEditRailScreen screen;
        private final List<AbstractWidget> widgets;
        private float vertCurveRadius;

        public VertCurveRadiusListEntry(float v, BrushEditRailScreen screen) {
            super(Text.literal(""), null, false);
            this.vertCurveRadius = v;
            this.screen = screen;
            setTooltipSupplier(() -> Optional.of(screen.getVerticalValueText(vertCurveRadius)));
            updateRadius(vertCurveRadius, false);
            radiusInput.setResponder(text -> {
                if (!text.isEmpty()) {
                    try {
                        float newRadius = Float.parseFloat(text);
                        updateRadius(newRadius, true);
                    } catch (Exception ignored) {}
                }
            });
            widgets = Lists.newArrayList(btnSetDefaultRadius, btnSetNoRadius, radiusInput);
        }

        public void updateRadius(float newRadius, boolean send) {
            btnSetDefaultRadius.active = newRadius != 0;
            btnSetNoRadius.active = newRadius >= 0;
            String expectedText = (newRadius <= 0) ? "" : Integer.toString((int) newRadius);
            if (!expectedText.equals(radiusInput.getValue())) {
                radiusInput.setValue(expectedText);
                radiusInput.setCursorPosition(0);
            }
            vertCurveRadius = newRadius;
            screen.updateRadius(newRadius, send);
        }

        @Override public boolean isEdited() { return false; }
        @Override public String getValue() { return ""; }
        @Override public Optional<String> getDefaultValue() { return Optional.empty(); }

        @Override
        public void render(GuiGraphics matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
            super.render(matrices, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
            IDrawing.setPositionAndWidth(radiusInput, 80, y, 200);
            IDrawing.setPositionAndWidth(btnSetDefaultRadius, 290, y, 60);
            IDrawing.setPositionAndWidth(btnSetNoRadius, 355, y, 60);
            radiusInput.render(matrices, mouseX, mouseY, delta);
            btnSetDefaultRadius.render(matrices, mouseX, mouseY, delta);
            btnSetNoRadius.render(matrices, mouseX, mouseY, delta);
        }

        @Override public List<? extends GuiEventListener> children() { return widgets; }
        @Override public List<? extends NarratableEntry> narratables() { return widgets; }
        @Override public void save() {}
    }
}