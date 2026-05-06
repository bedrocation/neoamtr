package cn.zbx1425.mtrsteamloco.render.scripting;

import cn.zbx1425.mtrsteamloco.Main;
import cn.zbx1425.mtrsteamloco.MainClient;
import cn.zbx1425.mtrsteamloco.render.scripting.util.*;
import cn.zbx1425.sowcer.math.Matrices;
import mtr.mappings.UtilitiesClient;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcerext.model.RawMesh;
import mtr.client.IDrawing;
import cn.zbx1425.sowcerext.model.RawModel;
import cn.zbx1425.sowcerext.model.ModelCluster;
import cn.zbx1425.sowcerext.model.integration.RawMeshBuilder;
import cn.zbx1425.sowcerext.util.ResourceUtil;
import mtr.client.ClientData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import vendor.cn.zbx1425.mtrsteamloco.org.mozilla.javascript.*;
import mtr.block.IBlock;
import net.minecraft.world.entity.player.Player;
import cn.zbx1425.mtrsteamloco.render.scripting.util.WrappedEntity;
import cn.zbx1425.mtrsteamloco.render.scripting.AbstractDrawCalls;
import cn.zbx1425.mtrsteamloco.ClientConfig;
import cn.zbx1425.mtrsteamloco.data.ShapeSerializer;
import cn.zbx1425.mtrsteamloco.data.ConfigResponder;
import net.minecraft.network.chat.Component;
import cn.zbx1425.mtrsteamloco.render.scripting.rail.RailDrawCalls.*;
import com.google.gson.JsonObject;
import cn.zbx1425.mtrsteamloco.CustomResources;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ScriptHolder {

    private static ExecutorService SCRIPT_THREAD = Executors.newSingleThreadExecutor();

    private Scriptable scope;
    public final List<Function> createFunctions = new ArrayList<>();
    public final List<Function> renderFunctions = new ArrayList<>();
    public final List<Function> disposeFunctions = new ArrayList<>();
    public final List<Function> useFunctions = new ArrayList<>();

    public long failTime = 0;
    public Exception failException = null;

    public String name;
    public String contextTypeName;
    private Map<ResourceLocation, String> scripts;

    private JsonObject config;
    private String key;

    public void load(String name, String contextTypeName, ResourceManager resourceManager, Map<ResourceLocation, String> scripts, JsonObject config, String key) throws Exception {
        this.name = name;
        this.contextTypeName = contextTypeName;
        this.scripts = scripts;
        this.createFunctions.clear();
        this.renderFunctions.clear();
        this.disposeFunctions.clear();
        this.useFunctions.clear();
        this.failTime = 0;
        this.failException = null;
        this.config = config;
        this.key = key;

        Context rhinoCtx = Context.enter();
        rhinoCtx.setLanguageVersion(Context.VERSION_ES6);
        try {
            scope = createImporter(rhinoCtx, config, key);

            // Run scripts
            ScriptResourceUtil.activeContext = rhinoCtx;
            ScriptResourceUtil.activeScope = scope;
            for (Map.Entry<ResourceLocation, String> entry : scripts.entrySet()) {
                String scriptStr = entry.getValue() == null
                        ? ResourceUtil.readResource(resourceManager, entry.getKey()) : entry.getValue();
                ScriptResourceUtil.executeScript(rhinoCtx, scope, entry.getKey(), scriptStr);
                acquireFunction("create", createFunctions);
                acquireFunction("create" + contextTypeName, createFunctions);
                acquireFunction("render", renderFunctions);
                acquireFunction("render" + contextTypeName, renderFunctions);
                acquireFunction("dispose", disposeFunctions);
                acquireFunction("dispose" + contextTypeName, disposeFunctions);
                acquireFunction("use", useFunctions);
                acquireFunction("use" + contextTypeName, useFunctions);
            }
            ScriptResourceUtil.activeContext = null;
            ScriptResourceUtil.activeScope = null;
        } finally {
            Context.exit();
        }
    }

    private static ImporterTopLevel createImporter(Context rhinoCtx, JsonObject config, String key) throws Exception {
        ImporterTopLevel scope = new ImporterTopLevel(rhinoCtx);

        // Populate Scope with global functions
        scope.put("include", scope, new NativeJavaMethod(
                ScriptResourceUtil.class.getMethod("includeScript", Object.class), "includeScript"));
        scope.put("print", scope, new NativeJavaMethod(
                ScriptResourceUtil.class.getMethod("print", Object[].class), "print"));
        scope.put("asJavaArray", scope, new NativeJavaMethod(
                JsFriendlyJavaUtils.class.getMethod("asJavaArray", List.class, Class.class), "asJavaArray"));

        scope.put("Resources", scope, new NativeJavaClass(scope, ScriptResourceUtil.class));
        scope.put("GraphicsTexture", scope, new NativeJavaClass(scope, GraphicsTexture.class));
        scope.put("Timing", scope, new NativeJavaClass(scope, TimingUtil.class));
        scope.put("StateTracker", scope, new NativeJavaClass(scope, StateTracker.class));
        scope.put("CycleTracker", scope, new NativeJavaClass(scope, CycleTracker.class));
        scope.put("RateLimit", scope, new NativeJavaClass(scope, RateLimit.class));
        scope.put("TextUtil", scope, new NativeJavaClass(scope, TextUtil.class));
        scope.put("SoundHelper", scope, new NativeJavaClass(scope, SoundHelper.class));
        scope.put("ParticleHelper", scope, new NativeJavaClass(scope, ParticleHelper.class));
        scope.put("TickableSound", scope, new NativeJavaClass(scope, TickableSound.class));
        scope.put("GlobalRegister", scope, new NativeJavaClass(scope, GlobalRegister.class));
        scope.put("WrappedEntity", scope, new NativeJavaClass(scope, WrappedEntity.class));
        scope.put("ComponentUtil", scope, new NativeJavaClass(scope, ComponentUtil.class));
        scope.put("IScreen", scope, new NativeJavaClass(scope, IScreen.class));
        scope.put("OrderedMap", scope, new NativeJavaClass(scope, OrderedMap.class));
        scope.put("PlacementOrder", scope, new NativeJavaClass(scope, OrderedMap.PlacementOrder.class));
        scope.put("ShapeSerializer", scope, new NativeJavaClass(scope, ShapeSerializer.class));
        scope.put("ConfigResponder", scope, new NativeJavaClass(scope, ConfigResponder.class));
        scope.put("ClientConfig", scope, new NativeJavaClass(scope, ClientConfig.class));
        scope.put("MinecraftClient", scope, new NativeJavaClass(scope, MinecraftClientUtil.class));

        scope.put("DrawCall", scope, new NativeJavaClass(scope, AbstractDrawCalls.DrawCall.class));
        scope.put("ClusterDrawCall", scope, new NativeJavaClass(scope, AbstractDrawCalls.ClusterDrawCall.class));
        scope.put("WorldDrawCall", scope, new NativeJavaClass(scope, AbstractDrawCalls.WorldDrawCall.class));
        scope.put("RailDrawCall", scope, new NativeJavaClass(scope, RailDrawCall.class));
        scope.put("SimpleRailDrawCall", scope, new NativeJavaClass(scope, SimpleRailDrawCall.class));

        scope.put("ModelManager", scope, Context.toObject(MainClient.modelManager, scope));
        scope.put("RawModel", scope, new NativeJavaClass(scope, RawModel.class));
        scope.put("RawMesh", scope, new NativeJavaClass(scope, RawMesh.class));
        scope.put("RawMeshBuilder", scope, new NativeJavaClass(scope, RawMeshBuilder.class));
        scope.put("ModelCluster", scope, new NativeJavaClass(scope, ModelCluster.class));
        scope.put("DynamicModelHolder", scope, new NativeJavaClass(scope, DynamicModelHolder.class));

        scope.put("Matrices", scope, new NativeJavaClass(scope, Matrices.class));
        scope.put("Matrix4f", scope, new NativeJavaClass(scope, Matrix4f.class));
        scope.put("Vector3f", scope, new NativeJavaClass(scope, Vector3f.class));


        scope.put("MTRClientData", scope, new NativeJavaClass(scope, ClientData.class));
        scope.put("IBlock", scope, new NativeJavaClass(scope, IBlock.class));
        scope.put("UtilitiesClient", scope, new NativeJavaClass(scope, UtilitiesClient.class));
        scope.put("IDrawing", scope, new NativeJavaClass(scope, IDrawing.class));

        scope.put("Component", scope, new NativeJavaClass(scope, Component.class));

        scope.put("Optional", scope, new NativeJavaClass(scope, Optional.class));

        JsonObject copy = config;
        if (!copy.has(key)) copy.addProperty("key", key);
        String jsonStr = new GsonBuilder().setPrettyPrinting().create().toJson(copy);
        scope.put("CONFIG_INFO", scope, jsonStr);
        String code = "CONFIG_INFO = JSON.parse(CONFIG_INFO);";
        rhinoCtx.evaluateString(scope, code, "parse CONFIG_INFO", 1, null);

            try {
                String[] classesToLoad = {
                        "util.AddParticleHelper",
                        "particle.MadParticleOption",
                        "particle.SpriteFrom",
                        "command.inheritable.InheritableBoolean",
                        "particle.ParticleRenderTypes",
                        "particle.ChangeMode"
                };
                for (String classToLoad : classesToLoad) {
                    Class<?> classToLoadClass = Class.forName("cn.ussshenzhou.madparticle." + classToLoad);
                    scope.put(classToLoad.substring(classToLoad.lastIndexOf(".") + 1), scope,
                            new NativeJavaClass(scope, classToLoadClass));
                }
                scope.put("foundMadParticle", scope, true);
            } catch (ClassNotFoundException ignored) {
                // Main.LOGGER.warn("MadParticle", ignored);
                scope.put("foundMadParticle", scope, false);
            }
            scope.put("CompoundTag", scope, new NativeJavaClass(scope, CompoundTag.class));

            rhinoCtx.evaluateString(scope, "\"use strict\"", "", 1, null);

        return scope;
    }

    public void reload(ResourceManager resourceManager) throws Exception {
        load(name, contextTypeName, resourceManager, scripts, config, key);
    }

    private void acquireFunction(String functionName, List<Function> target) {
        Object jsFunction = scope.get(functionName, scope);
        if (jsFunction != Scriptable.NOT_FOUND) {
            if (jsFunction instanceof Function) {
                target.add((Function)jsFunction);
            }
            scope.delete(functionName);
        }
    }

    public Future<?> callFunctionAsync(List<Function> functions, AbstractScriptContext scriptCtx, Runnable finishCallback, Object... args) {
        if (duringFailTimeout()) return null;
        failTime = 0;
        return SCRIPT_THREAD.submit(() -> {
            if (Thread.currentThread().isInterrupted()) return;
            Context rhinoCtx = Context.enter();
            if (scriptCtx.state == null) scriptCtx.state = rhinoCtx.newObject(scope);
            try {
                long startTime = System.nanoTime();

                TimingUtil.prepareForScript(scriptCtx);
                Object[] functionParam = new Object[3 + args.length];
                functionParam[0] = scriptCtx;
                functionParam[1] = scriptCtx.state;
                functionParam[2] = scriptCtx.getWrapperObject();
                System.arraycopy(args, 0, functionParam, 3, args.length);

                for (Function function : functions) {
                    function.call(rhinoCtx, scope, scope, functionParam);
                }
                if (finishCallback != null) finishCallback.run();
                scriptCtx.lastExecuteDuration = System.nanoTime() - startTime;
            } catch (Exception ex) {
                Main.LOGGER.error("Error in ANTE Resource Pack JavaScript", ex);
                failTime = System.currentTimeMillis();
                failException = ex;
            } finally {
                Context.exit();
            }
        });
    }

    public void tryCallRenderFunctionAsync(AbstractScriptContext scriptCtx) {
        if (!(scriptCtx.scriptStatus == null || scriptCtx.scriptStatus.isDone())) return;
        if (scriptCtx.disposed) return;
        if (!scriptCtx.created) {
            ScriptContextManager.trackContext(scriptCtx, this);
            scriptCtx.scriptStatus = callFunctionAsync(createFunctions, scriptCtx, () -> {
                scriptCtx.created = true;
            });
            return;
        }
        if (scriptCtx.scriptStatus == null || scriptCtx.scriptStatus.isDone()) {
            scriptCtx.scriptStatus = callFunctionAsync(renderFunctions, scriptCtx, scriptCtx::renderFunctionFinished);
        }
    }

    public void tryCallDisposeFunctionAsync(AbstractScriptContext scriptCtx) {
        if (!(scriptCtx.scriptStatus == null || scriptCtx.scriptStatus.isDone())) return;
        scriptCtx.disposed = true;
        if (scriptCtx.created) {
            scriptCtx.scriptStatus = callFunctionAsync(disposeFunctions, scriptCtx, () -> {
                scriptCtx.created = false;
            });
        }
    }

    public void tryCallBeClickedFunctionAsync(AbstractScriptContext scriptCtx, Player player) {
        if (!(scriptCtx.scriptStatus == null || scriptCtx.scriptStatus.isDone())) return;
        if (scriptCtx.disposed) return;
        if (scriptCtx.created) {
            scriptCtx.scriptStatus = callFunctionAsync(useFunctions, scriptCtx, null, new WrappedEntity(player));
        }
    }

    private boolean duringFailTimeout() {
        return failTime > 0 && (System.currentTimeMillis() - failTime) < 4000;
    }

    public static void resetRunner() {
        SCRIPT_THREAD.shutdownNow();
        SCRIPT_THREAD = Executors.newSingleThreadExecutor();
    }
}
