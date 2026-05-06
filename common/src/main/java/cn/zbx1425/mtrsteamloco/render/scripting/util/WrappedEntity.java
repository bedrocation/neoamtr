package cn.zbx1425.mtrsteamloco.render.scripting.util;

import net.minecraft.world.entity.Entity;
import cn.zbx1425.sowcer.math.Vector3f;

public class WrappedEntity {
    public Entity entity;
    
    public WrappedEntity(Entity entity) {
        this.entity = entity;
    }

    public double getX() {
        return entity.getX();
    }

    public double getY() {
        return entity.getY();
    }

    public double getZ() {
        return entity.getZ();
    }

    public Vector3f getLookAngle() {
        return new Vector3f(entity.getLookAngle());
    }

    public Vector3f getPosition() {
        return new Vector3f((float)getX(), (float)getY(), (float)getZ());
    }

    public boolean isShiftKeyDown() {
        return entity.isShiftKeyDown();
    }
}