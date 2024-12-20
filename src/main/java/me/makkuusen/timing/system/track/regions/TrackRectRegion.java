package me.makkuusen.timing.system.track.regions;

import lombok.Getter;
import co.aikar.idb.DbRow;
import me.makkuusen.timing.system.TimingSystem;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import com.sk89q.worldedit.math.BlockVector2;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Getter
public class TrackRectRegion extends TrackRegion {

    private List<BlockVector2> pointList;
    private Vector left;
    private Vector right;
    private Vector third;
    private Vector dirVec;
    private double maxDist;
    private Vector dirLatVec;
    private double maxDistLat;

    public TrackRectRegion(DbRow data, List<BlockVector2> points) {
        super(data);
        setShape(RegionShape.RECT);
        updateRegion(points);
    }

    public TrackRectRegion(long id, long trackId, int regionIndex, RegionType regionType, Location spawnLocation, Location minP, Location maxP, List<BlockVector2> points) {
        super(id,trackId,regionIndex,regionType,spawnLocation,minP,maxP);
        setShape(RegionShape.RECT);
        updateRegion(points);
    }

    public void updateRegion(List<BlockVector2> points) {
        try {
            TimingSystem.getTrackDatabase().deletePoint(getId());
            for (BlockVector2 v : points) {
                TimingSystem.getTrackDatabase().createPoint(getId(), v);
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
            return;
        }
        pointList = new ArrayList<BlockVector2>(points);
        // Why do vector functions modify them inplace????
        // add 0.5 to the region start from the centre of blocks instead of the negative edge
        Vector offset = new Vector(0.5, 0.5, 0.5);
        left = new Vector(points.get(0).getX(), getMinP().getY(), points.get(0).getZ()).add(offset);
        right = new Vector(points.get(1).getX(), getMinP().getY(), points.get(1).getZ()).add(offset);
        third = new Vector(points.get(2).getX(), getMaxP().getY(), points.get(2).getZ()).add(offset);
        Vector diffLat = left.clone().subtract(right);
        maxDistLat = diffLat.length();
        dirLatVec = diffLat.clone().multiply(1 / maxDistLat);
        dirVec = dirLatVec.clone().rotateAroundY(-Math.PI / 2);
        Vector diff = third.clone().subtract(right);
        maxDist = diff.dot(dirVec);
    }

    public boolean contains(Location loc) {
        if (loc == null || getMinP() == null || getMaxP() == null) {
            return false;
        } else if (loc.getY() > third.getY() || loc.getY() < right.getY()) {
            return false;
        } else {
            Vector relative = loc.toVector().subtract(right);
            double distLat = relative.dot(dirLatVec);
            if (distLat < 0 || distLat > maxDistLat) return false;
            double dist = relative.dot(dirVec);
            if (maxDist >= 0) {
                return (dist >= 0 && dist <= maxDist);
            } else {
                return (dist <= 0 && dist >= maxDist);
            }
        }
    }

    public int passTime(Location last, Location curr) {
        // only called if contains(curr) returns true
        if (last == null) {
            return 0;
        } else {
            double distanceAfter = curr.toVector().clone().subtract(right).dot(dirVec);
            double distanceBefore = last.toVector().clone().subtract(right).dot(dirVec);
            double ticks = distanceBefore / (distanceBefore - distanceAfter);
            ticks = Math.min(1, Math.max(0, ticks));
            return (int) ((1 - ticks) * 50);
        }
    }

    public boolean isDefined() {
        return true;
    }

    public boolean hasEqualBounds(TrackRegion other) {
        if (other instanceof TrackRectRegion trackRectRegion) {
            if (!other.getWorldName().equalsIgnoreCase(getWorldName())) {
                return false;
            }
            if (!isDefined() || !other.isDefined()) {
                return false;
            }
            return getMinP().equals(trackRectRegion.getMinP()) && getMaxP().equals(trackRectRegion.getMaxP());
        }
        return false;
    }
}
