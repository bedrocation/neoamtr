package cn.zbx1425.mtrsteamloco.render.scripting.rail;

import cn.zbx1425.mtrsteamloco.render.scripting.AbstractScriptContext;
import mtr.data.Rail;
import vendor.cn.zbx1425.mtrsteamloco.org.mozilla.javascript.NativeObject;
import net.minecraft.client.renderer.culling.Frustum;
import cn.zbx1425.sowcerext.reuse.DrawScheduler;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.math.Matrices;
import cn.zbx1425.sowcerext.model.ModelCluster;
import cn.zbx1425.mtrsteamloco.render.scripting.util.DynamicModelHolder;
import static cn.zbx1425.mtrsteamloco.render.scripting.rail.RailDrawCalls.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collection;

public class RailScriptContext extends AbstractScriptContext {

    public final RailWrapper rail;
    public final Map<Object, RailDrawCall> drawCalls = new HashMap<>();
    public List<RailDrawCall> scriptResult = new ArrayList<>();
    public List<RailDrawCall> scriptResultWriting = new ArrayList<>();
    public boolean living = true;

    public RailScriptContext(Rail rail) {
        this.rail = new RailWrapper(rail);
    }

    public void commit(DrawScheduler drawScheduler, Matrix4f world, Frustum frustum, Vector3f cameraPos, int maxRailDistance) {
        for (RailDrawCall drawCall : scriptResult) {
            drawCall.commit(drawScheduler, world, frustum, cameraPos, maxRailDistance);
        }
        Collection<RailDrawCall> set = drawCalls.values();
        for (RailDrawCall drawCall : set) {
            drawCall.commit(drawScheduler, world, frustum, cameraPos, maxRailDistance);
        }
    }

    @Override
    public void renderFunctionFinished() {
        scriptResult = scriptResultWriting;
        scriptResultWriting = new ArrayList<>();
    }

    @Override
    public Object getWrapperObject() {
        return rail;
    }

    public void dispose() {
        living = false;
    }

    @Override
    public boolean isBearerAlive() {
        return living && !disposed;
    }

    public void drawModel(ModelCluster model, Matrices matrices) {
        scriptResultWriting.add(new SimpleRailDrawCall(model, matrices.last()));
    }

    public void drawModel(DynamicModelHolder model, Matrices matrices) {
        scriptResultWriting.add(new SimpleRailDrawCall(model, matrices.last()));
    }

}