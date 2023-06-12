package smartin.miapi.modules.properties;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.registry.Registry;
import smartin.miapi.Miapi;
import smartin.miapi.datapack.ReloadEvents;
import smartin.miapi.modules.ItemModule;
import smartin.miapi.item.modular.StatResolver;
import smartin.miapi.modules.properties.util.MergeType;
import smartin.miapi.modules.properties.util.ModuleProperty;

import javax.annotation.Nullable;
import java.util.*;

/**
 * This is the Property relating to materials of a Module
 */
public class MaterialProperty implements ModuleProperty {
    public static final String KEY = "material";
    public static ModuleProperty property;
    public static Map<String, Material> materials = new HashMap<>();

    public MaterialProperty() {
        property = this;
        StatResolver.registerResolver(KEY, new StatResolver.Resolver() {
            @Override
            public double resolveDouble(String data, ItemModule.ModuleInstance instance) {
                JsonElement jsonData = instance.getKeyedProperties().get(KEY);
                try {
                    if (jsonData != null) {
                        String materialKey = jsonData.getAsString();
                        Material material = materials.get(materialKey);
                        if(material!=null){
                            jsonData = materials.get(jsonData.getAsString()).rawJson;
                            if (jsonData != null) {
                                String[] keys = data.split("\\.");
                                for (String key : keys) {
                                    jsonData = jsonData.getAsJsonObject().get(key);
                                    if (jsonData == null) {
                                        break;
                                    }
                                }
                                if (jsonData != null) {
                                    return jsonData.getAsDouble();
                                }
                            }
                        }
                    }
                } catch (Exception exception){
                    Miapi.LOGGER.warn("Error during Material Resolve");
                    Miapi.LOGGER.error(exception.getMessage());
                    exception.printStackTrace();
                }
                return 0;
            }

            @Override
            public String resolveString(String data, ItemModule.ModuleInstance instance) {
                JsonElement jsonData = instance.getProperties().get(property);
                try {
                    if (jsonData != null) {
                        String materialKey = jsonData.getAsString();
                        Material material = materials.get(materialKey);
                        if(material!=null){
                            jsonData = materials.get(jsonData.getAsString()).rawJson;
                            if (jsonData != null) {
                                String[] keys = data.split("\\.");
                                for (String key : keys) {
                                    jsonData = jsonData.getAsJsonObject().get(key);
                                    if (jsonData == null) {
                                        break;
                                    }
                                }
                                if (jsonData != null) {
                                    return jsonData.getAsString();
                                }
                            }
                        }
                        else{
                            Miapi.LOGGER.warn("Material "+ materialKey + " not found");
                        }
                    }
                } catch (Exception exception){
                    Miapi.LOGGER.warn("Error during Material Resolve");
                    exception.printStackTrace();
                }
                return "";
            }
        });
        ReloadEvents.MAIN.subscribe((isClient -> {
            materials.clear();
            ReloadEvents.DATA_PACKS.forEach((path, data) -> {
                if (path.contains(KEY)) {
                    //load Material Logic
                    JsonParser parser = new JsonParser();
                    JsonObject obj = parser.parse(data).getAsJsonObject();
                    String key = obj.get("key").getAsString();
                    Material material = new Material();
                    material.key = key;
                    material.rawJson = obj;
                    materials.put(key, material);
                }
            });
        }), -1.0f);
    }

    public static List<String> getTextureKeys() {
        Set<String> textureKeys = new HashSet<>();
        textureKeys.add("base");
        for (Material material : materials.values()) {
            JsonArray textures = material.rawJson.getAsJsonObject().getAsJsonArray("textures");
            for (JsonElement texture : textures) {
                textureKeys.add(texture.getAsString());
            }
        }
        return new ArrayList<>(textureKeys);
    }

    @Override
    public boolean load(String moduleKey, JsonElement data) throws Exception {
        return true;
    }

    @Override
    public JsonElement merge(JsonElement old, JsonElement toMerge, MergeType type) {
        switch (type) {
            case EXTEND -> {
                return old;
            }
            case SMART, OVERWRITE -> {
                return toMerge;
            }
        }
        return old;
    }

    @Nullable
    public static Material getMaterial(ItemStack item) {
        for (Material material : materials.values()) {
            JsonObject obj = material.rawJson.getAsJsonObject();
            JsonArray items = obj.getAsJsonArray("items");

            for (JsonElement element : items) {
                JsonObject itemObj = element.getAsJsonObject();

                if (itemObj.has("item")) {
                    String itemId = itemObj.get("item").getAsString();
                    if (Registry.ITEM.getId(item.getItem()).toString().equals(itemId)) {
                        return material;
                    }
                } else if (itemObj.has("tag")) {
                    String tagId = itemObj.get("tag").getAsString();
                    TagKey<Item> tag = TagKey.of(Registry.ITEM_KEY, new Identifier(tagId));
                    if (tag != null && item.isIn(tag)) {
                        return material;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public static Material getMaterial(JsonElement element) {
        if (element != null) {
            return materials.get(element.getAsString());
        }
        return null;
    }

    @Nullable
    public static Material getMaterial(ItemModule.ModuleInstance instance) {
        JsonElement element = instance.getProperties().get(property);
        if (element != null) {
            return materials.get(element.getAsString());
        }
        return null;
    }

    public static void setMaterial(ItemModule.ModuleInstance instance, String material) {
        String propertyString = instance.moduleData.computeIfAbsent("properties", (key) -> {
            return "{material:empty}";
        });
        JsonObject moduleJson = Miapi.gson.fromJson(propertyString, JsonObject.class);
        moduleJson.addProperty("material", material);
        instance.moduleData.put("properties", Miapi.gson.toJson(moduleJson));
    }

    public class Material {
        public String key;
        public JsonElement rawJson;

        public List<String> getGroups() {
            List<String> groups = new ArrayList<>();
            groups.add(key);
            JsonArray groupsJson = rawJson.getAsJsonObject().getAsJsonArray("groups");
            for (JsonElement groupElement : groupsJson) {
                String group = groupElement.getAsString();
                groups.add(group);
            }
            return groups;
        }

        public List<String> getTextureKeys() {
            List<String> textureKeys = new ArrayList<>();
            JsonArray textures = rawJson.getAsJsonObject().getAsJsonArray("textures");
            for (JsonElement texture : textures) {
                textureKeys.add(texture.getAsString());
            }
            textureKeys.add("default");
            return new ArrayList<>(textureKeys);
        }

        public int getColor() {
            long longValue = Long.parseLong(rawJson.getAsJsonObject().get("color").getAsString(), 16);
            return (int) (longValue & 0xffffffffL);
        }

        public static int getColor(String color){
            if(color.equals("")) return ColorHelper.Argb.getArgb(255,255,255,255);
            long longValue = Long.parseLong(color, 16);
            return (int) (longValue & 0xffffffffL);
        }

        public double getValueOfItem(ItemStack item) {
            JsonArray items = rawJson.getAsJsonObject().getAsJsonArray("items");

            for (JsonElement element : items) {
                JsonObject itemObj = element.getAsJsonObject();

                if (itemObj.has("item")) {
                    String itemId = itemObj.get("item").getAsString();
                    if (Registry.ITEM.getId(item.getItem()).toString().equals(itemId)) {
                        try {
                            return itemObj.get("value").getAsDouble();
                        } catch (Exception surpressed) {
                            return 1;
                        }
                    }
                } else if (itemObj.has("tag")) {
                    String tagId = itemObj.get("tag").getAsString();
                    TagKey<Item> tag = TagKey.of(Registry.ITEM_KEY, new Identifier(tagId));
                    if (tag != null && item.isIn(tag)) {
                        try {
                            return itemObj.get("value").getAsDouble();
                        } catch (Exception suppressed) {
                            return 1;
                        }
                    }
                }
            }
            return 0;
        }
    }
}
