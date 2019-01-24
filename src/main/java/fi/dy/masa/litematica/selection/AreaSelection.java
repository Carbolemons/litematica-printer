package fi.dy.masa.litematica.selection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.litematica.util.PositionUtils.CoordinateType;
import fi.dy.masa.litematica.util.PositionUtils.Corner;
import fi.dy.masa.malilib.util.JsonUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public class AreaSelection
{
    protected final Map<String, Box> subRegionBoxes = new HashMap<>();
    protected String name = "Unnamed";
    protected boolean originSelected;
    protected BlockPos calculatedOrigin = BlockPos.ORIGIN;
    protected boolean calculatedOriginDirty = true;
    @Nullable protected BlockPos explicitOrigin = null;
    @Nullable protected String currentBox;

    public static AreaSelection fromPlacement(SchematicPlacement placement)
    {
        ImmutableMap<String, Box> boxes = placement.getSubRegionBoxes(RequiredEnabled.ANY);
        BlockPos origin = placement.getOrigin();

        AreaSelection selection = new AreaSelection();
        selection.setExplicitOrigin(origin);
        selection.name = placement.getName();
        selection.subRegionBoxes.putAll(boxes);

        return selection;
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @Nullable
    public String getCurrentSubRegionBoxName()
    {
        return this.currentBox;
    }

    public boolean setSelectedSubRegionBox(@Nullable String name)
    {
        if (name == null || this.subRegionBoxes.containsKey(name))
        {
            this.currentBox = name;
            return true;
        }

        return false;
    }

    public boolean isOriginSelected()
    {
        return this.originSelected;
    }

    public void setOriginSelected(boolean selected)
    {
        this.originSelected = selected;
    }

    /**
     * Returns the effective origin point. This is the explicit origin point, if one has been set,
     * otherwise it's an automatically calculated origin point, located at the minimum corner
     * of all the boxes.
     * @return
     */
    public BlockPos getEffectiveOrigin()
    {
        if (this.explicitOrigin != null)
        {
            return this.explicitOrigin;
        }
        else
        {
            if (this.calculatedOriginDirty)
            {
                this.updateCalculatedOrigin();
            }

            return this.calculatedOrigin;
        }
    }

    /**
     * Get the explicitly defined origin point, if any.
     * @return
     */
    @Nullable
    public BlockPos getExplicitOrigin()
    {
        return this.explicitOrigin;
    }

    public void setExplicitOrigin(@Nullable BlockPos origin)
    {
        this.explicitOrigin = origin;

        if (origin == null)
        {
            this.originSelected = false;
        }
    }

    protected void updateCalculatedOrigin()
    {
        Pair<BlockPos, BlockPos> pair = PositionUtils.getEnclosingAreaCorners(this.subRegionBoxes.values());

        if (pair != null)
        {
            this.calculatedOrigin = pair.getLeft();
        }
        else
        {
            this.calculatedOrigin = BlockPos.ORIGIN;
        }

        this.calculatedOriginDirty = false;
    }

    @Nullable
    public Box getSubRegionBox(String name)
    {
        return this.subRegionBoxes.get(name);
    }

    @Nullable
    public Box getSelectedSubRegionBox()
    {
        return this.currentBox != null ? this.subRegionBoxes.get(this.currentBox) : null;
    }

    public List<Box> getAllSubRegionBoxes()
    {
        return ImmutableList.copyOf(this.subRegionBoxes.values());
    }

    @Nullable
    public String createNewSubRegionBox(BlockPos pos1, final String nameIn)
    {
        this.clearCurrentSelectedCorner();
        this.setOriginSelected(false);

        String name = nameIn;
        int i = 1;

        while (this.subRegionBoxes.containsKey(name))
        {
            name = nameIn + " " + i;
            i++;
        }

        Box box = new Box();
        box.setName(name);
        box.setSelectedCorner(Corner.CORNER_1);
        this.currentBox = name;
        this.subRegionBoxes.put(name, box);
        this.setSubRegionCornerPos(box, Corner.CORNER_1, pos1);

        return name;
    }

    public void clearCurrentSelectedCorner()
    {
        Box box = this.getSelectedSubRegionBox();

        if (box != null)
        {
            box.setSelectedCorner(Corner.NONE);
        }
    }

    /**
     * Adds the given SelectionBox, if either replace is true, or there isn't yet a box by the same name.
     * @param box
     * @param replace
     * @return true if the box was successfully added, false if replace was false and there was already a box with the same name
     */
    public boolean addSubRegionBox(Box box, boolean replace)
    {
        if (replace || this.subRegionBoxes.containsKey(box.getName()) == false)
        {
            this.subRegionBoxes.put(box.getName(), box);
            return true;
        }

        return false;
    }

    public void removeAllSubRegionBoxes()
    {
        this.subRegionBoxes.clear();
    }

    public boolean removeSubRegionBox(String name)
    {
        return this.subRegionBoxes.remove(name) != null;
    }

    public boolean removeSelectedSubRegionBox()
    {
        boolean success = this.currentBox != null ? this.subRegionBoxes.remove(this.currentBox) != null : false;
        this.currentBox = null;
        return success;
    }

    public boolean renameSubRegionBox(String oldName, String newName)
    {
        Box box = this.subRegionBoxes.get(oldName);

        if (box != null && this.subRegionBoxes.containsKey(newName) == false)
        {
            this.subRegionBoxes.remove(oldName);
            box.setName(newName);
            this.subRegionBoxes.put(newName, box);

            if (this.currentBox != null && this.currentBox.equals(oldName))
            {
                this.currentBox = newName;
            }

            return true;
        }

        return false;
    }

    public void moveEntireSelectionTo(BlockPos newOrigin, boolean printMessage)
    {
        BlockPos old = this.getEffectiveOrigin();
        BlockPos diff = newOrigin.subtract(old);

        for (Box box : this.subRegionBoxes.values())
        {
            if (box.getPos1() != null)
            {
                this.setSubRegionCornerPos(box, Corner.CORNER_1, box.getPos1().add(diff));
            }

            if (box.getPos2() != null)
            {
                this.setSubRegionCornerPos(box, Corner.CORNER_2, box.getPos2().add(diff));
            }
        }

        if (this.getExplicitOrigin() != null)
        {
            this.setExplicitOrigin(newOrigin);
        }

        if (printMessage)
        {
            String oldStr = String.format("x: %d, y: %d, z: %d", old.getX(), old.getY(), old.getZ());
            String newStr = String.format("x: %d, y: %d, z: %d", newOrigin.getX(), newOrigin.getY(), newOrigin.getZ());
            StringUtils.printActionbarMessage("litematica.message.moved_selection", oldStr, newStr);
        }
    }

    public void moveSelectedElement(EnumFacing direction, int amount)
    {
        Box box = this.getSelectedSubRegionBox();

        if (this.isOriginSelected())
        {
            if (this.getExplicitOrigin() != null)
            {
                this.setExplicitOrigin(this.getExplicitOrigin().offset(direction, amount));
            }
        }
        else if (box != null)
        {
            Corner corner = box.getSelectedCorner();

            if ((corner == Corner.NONE || corner == Corner.CORNER_1) && box.getPos1() != null)
            {
                BlockPos pos = this.getSubRegionCornerPos(box, Corner.CORNER_1).offset(direction, amount);
                this.setSubRegionCornerPos(box, Corner.CORNER_1, pos);
            }

            if ((corner == Corner.NONE || corner == Corner.CORNER_2) && box.getPos2() != null)
            {
                BlockPos pos = this.getSubRegionCornerPos(box, Corner.CORNER_2).offset(direction, amount);
                this.setSubRegionCornerPos(box, Corner.CORNER_2, pos);
            }
        }
    }

    public void setSelectedSubRegionCornerPos(BlockPos pos, Corner corner)
    {
        Box box = this.getSelectedSubRegionBox();

        if (box != null)
        {
            this.setSubRegionCornerPos(box, corner, pos);
        }
    }

    public void setSubRegionCornerPos(Box box, Corner corner, BlockPos pos)
    {
        if (corner == Corner.CORNER_1)
        {
            box.setPos1(pos);
            this.calculatedOriginDirty = true;
        }
        else if (corner == Corner.CORNER_2)
        {
            box.setPos2(pos);
            this.calculatedOriginDirty = true;
        }
    }

    public void setCoordinate(Box box, Corner corner, CoordinateType type, int value)
    {
        if (corner != null && corner != Corner.NONE)
        {
            box.setCoordinate(value, corner, type);
            this.calculatedOriginDirty = true;
        }
        else if (this.explicitOrigin != null)
        {
            this.setExplicitOrigin(PositionUtils.getModifiedPosition(this.explicitOrigin, value, type));
        }
    }

    public BlockPos getSubRegionCornerPos(Box box, Corner corner)
    {
        return corner == Corner.CORNER_2 ? box.getPos2() : box.getPos1();
    }

    public static AreaSelection fromJson(JsonObject obj)
    {
        AreaSelection area = new AreaSelection();

        if (JsonUtils.hasArray(obj, "boxes"))
        {
            JsonArray arr = obj.get("boxes").getAsJsonArray();
            final int size = arr.size();

            for (int i = 0; i < size; i++)
            {
                JsonElement el = arr.get(i);

                if (el.isJsonObject())
                {
                    Box box = Box.fromJson(el.getAsJsonObject());

                    if (box != null)
                    {
                        area.subRegionBoxes.put(box.getName(), box);
                    }
                }
            }
        }

        if (JsonUtils.hasString(obj, "name"))
        {
            area.name = obj.get("name").getAsString();
        }

        if (JsonUtils.hasString(obj, "current"))
        {
            area.currentBox = obj.get("current").getAsString();
        }

        BlockPos pos = JsonUtils.blockPosFromJson(obj, "origin");

        if (pos != null)
        {
            area.setExplicitOrigin(pos);
        }
        else
        {
            area.updateCalculatedOrigin();
        }

        return area;
    }

    public JsonObject toJson()
    {
        JsonObject obj = new JsonObject();
        JsonArray arr = new JsonArray();

        for (Box box : this.subRegionBoxes.values())
        {
            JsonObject o = box.toJson();

            if (o != null)
            {
                arr.add(o);
            }
        }

        obj.add("name", new JsonPrimitive(this.name));

        if (arr.size() > 0)
        {
            if (this.currentBox != null)
            {
                obj.add("current", new JsonPrimitive(this.currentBox));
            }

            obj.add("boxes", arr);
        }

        if (this.getExplicitOrigin() != null)
        {
            obj.add("origin", JsonUtils.blockPosToJson(this.getExplicitOrigin()));
        }

        return obj;
    }
}
