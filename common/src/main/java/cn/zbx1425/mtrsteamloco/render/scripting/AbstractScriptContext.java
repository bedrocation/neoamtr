package cn.zbx1425.mtrsteamloco.render.scripting;

import vendor.cn.zbx1425.mtrsteamloco.org.mozilla.javascript.Scriptable;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import cn.zbx1425.mtrsteamloco.render.scripting.util.OrderedMap;
import cn.zbx1425.mtrsteamloco.render.scripting.AbstractDrawCalls.*;

public abstract class AbstractScriptContext {

    public Scriptable state;
    public boolean created = false;
    public Future<?> scriptStatus;
    public double lastExecuteTime = 0;

    public boolean disposed = false;

    public long lastExecuteDuration = 0;
    public OrderedMap<String, Object> debugInfo = new OrderedMap<>();

    public abstract void renderFunctionFinished();

    public abstract Object getWrapperObject();

    public abstract boolean isBearerAlive();

    public OrderedMap<String, Object> getDebugInfo() {
        synchronized (debugInfo) {
            return new OrderedMap<>(debugInfo);
        }
    }

    public void removeDebugInfo(String key) {
        synchronized (debugInfo) {
            debugInfo.remove(key);
        }
    }

    public void setDebugInfo(String key, Object... values) {
        synchronized (debugInfo) {
            OrderedMap.PlacementOrder order = OrderedMap.PlacementOrder.CENTRAL;

            List<Object> list = new ArrayList<>();

            if (values == null || values.length == 0) return;
            for (Object value : values) {
                list.add(value);
            }
            if (list.size() > 1) {
                if (list.get(0) instanceof OrderedMap.PlacementOrder) {
                    order = (OrderedMap.PlacementOrder) list.remove(0);
                }
            }
            if (list.size() == 1) {
                debugInfo.put(key, list.get(0), order);
            } else {
                debugInfo.put(key, list, order);
            }
        }
    }

    public void clearDebugInfo() {
        synchronized (debugInfo) {
            debugInfo.clear();
        }
    }
}
