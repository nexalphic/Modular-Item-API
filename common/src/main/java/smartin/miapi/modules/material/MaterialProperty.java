package smartin.miapi.modules.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.resource.ResourceReloader;
import org.jetbrains.annotations.Nullable;
import smartin.miapi.Miapi;
import smartin.miapi.client.MiapiClient;
import smartin.miapi.datapack.ReloadEvents;
import smartin.miapi.item.modular.StatResolver;
import smartin.miapi.modules.ItemModule;
import smartin.miapi.modules.properties.util.MergeType;
import smartin.miapi.modules.properties.util.ModuleProperty;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

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
                        if (material != null) {
                            return material.getDouble(data);
                        }
                    }
                } catch (Exception exception) {
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
                        if (material != null) {
                            return material.getData(data);
                        } else {
                            Miapi.LOGGER.warn("Material " + materialKey + " not found");
                        }
                    }
                } catch (Exception exception) {
                    Miapi.LOGGER.warn("Error during Material Resolve");
                    exception.printStackTrace();
                }
                return "";
            }
        });
        Miapi.registerReloadHandler(ReloadEvents.MAIN, "materials", materials, (isClient, path, data) -> {
            JsonParser parser = new JsonParser();
            JsonObject obj = parser.parse(data).getAsJsonObject();
            JsonMaterial material = new JsonMaterial(obj, isClient);
            if (materials.containsKey(material.getKey())) {
                Miapi.LOGGER.warn("Overwriting Materials isnt supported yet and may cause issues. Material from  " + path + " is overwriting " + material.getKey());
            }
            materials.put(material.getKey(), material);
        }, -2f);
        ReloadEvents.MAIN.subscribe((isClient) -> {
            List<ToolItem> toolItems = Registries.ITEM.stream()
                    .filter(ToolItem.class::isInstance)
                    .map(ToolItem.class::cast)
                    .toList();
            toolItems.stream()
                    .map(toolItem -> toolItem.getMaterial())
                    .collect(Collectors.toSet())
                    .stream()
                    .filter(toolMaterial -> toolMaterial.getRepairIngredient().getMatchingStacks().length > 0)
                    .filter(toolMaterial -> Arrays.stream(toolMaterial.getRepairIngredient().getMatchingStacks()).allMatch(itemStack -> getMaterialFromIngredient(itemStack) == null && !itemStack.getItem().equals(Items.BARRIER)))
                    .collect(Collectors.toSet()).forEach(toolMaterial -> {
                        GeneratedMaterial generatedMaterial = new GeneratedMaterial(toolMaterial, isClient);
                        if (generatedMaterial.assignStats(toolItems)) {
                            materials.put(generatedMaterial.getKey(), generatedMaterial);
                        } else {
                            Miapi.LOGGER.warn("Couldn't correctly setup material for " + generatedMaterial.mainIngredient.getItem());
                        }
                    });
            Registries.ITEM.stream().filter(item -> item.getDefaultStack().isIn(ItemTags.PLANKS)).forEach(item -> {
                if (getMaterialFromIngredient(item.getDefaultStack()) == null) {
                    GeneratedMaterial generatedMaterial = new GeneratedMaterial(ToolMaterials.WOOD, item.getDefaultStack(), isClient);
                    materials.put(generatedMaterial.getKey(), generatedMaterial);
                    generatedMaterial.copyStatsFrom(materials.get("wood"));
                }
            });
            Registries.ITEM.stream().filter(item -> item.getDefaultStack().isIn(ItemTags.STONE_TOOL_MATERIALS)).forEach(item -> {
                if (getMaterialFromIngredient(item.getDefaultStack()) == null) {
                    GeneratedMaterial generatedMaterial = new GeneratedMaterial(ToolMaterials.STONE, item.getDefaultStack(), isClient);
                    materials.put(generatedMaterial.getKey(), generatedMaterial);
                    generatedMaterial.copyStatsFrom(materials.get("stone"));
                }
            });
        }, -1f);
        ReloadEvents.END.subscribe((isClient) -> {
            if (isClient) {
                MinecraftClient.getInstance().execute(() -> {
                    Executor prepare = new CurrentThreadExecutor();
                    Executor done = new CurrentThreadExecutor();
                    RenderSystem.assertOnRenderThread();
                    MinecraftClient.getInstance().getTextureManager();
                    ResourceReloader.Synchronizer synchronizer = new ResourceReloader.Synchronizer() {
                        @Override
                        public <T> CompletableFuture<T> whenPrepared(T preparedObject) {
                            return CompletableFuture.completedFuture(preparedObject);
                        }
                    };

                    MiapiClient.materialAtlasManager.reload(synchronizer, MinecraftClient.getInstance().getResourceManager(), MinecraftClient.getInstance().getProfiler(), MinecraftClient.getInstance().getProfiler(), prepare, done);
                });

            }
        }, 1);
        ReloadEvents.END.subscribe((isClient -> {
            Miapi.LOGGER.info("Loaded " + materials.size() + " Materials");
        }));
    }

    public static class CurrentThreadExecutor implements Executor {
        public void execute(Runnable r) {
            r.run();
        }
    }

    public static List<String> getTextureKeys() {
        Set<String> textureKeys = new HashSet<>();
        textureKeys.add("base");
        for (Material material : materials.values()) {
            textureKeys.add(material.getKey());
            JsonElement textureJson = material.getRawElement("textures");
            if (textureJson != null && textureJson.isJsonArray()) {
                JsonArray textures = material.getRawElement("textures").getAsJsonArray();
                for (JsonElement texture : textures) {
                    textureKeys.add(texture.getAsString());
                }
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

    /**
     * Resolves a Material form an Itemstack. if no Material is set for the Itemstack, returns null
     * @param item
     * @return
     */
    @Nullable
    public static Material getMaterialFromIngredient(ItemStack item) {
        double lowestPrio = Double.MAX_VALUE;
        Material foundMaterial = null;
        for (Material material : materials.values()) {
            Double matPrio = material.getPriorityOfIngredientItem(item);
            if (matPrio != null && matPrio < lowestPrio) {
                foundMaterial = material;
            }
        }
        if(foundMaterial!=null){
            return foundMaterial.getMaterialFromIngredient(item);
        }
        else{
            return null;
        }
    }

    /**
     * This call should only used if no valid moduleinstance is known and only the Key of the material is important
     *
     * @param element
     * @return
     */
    @Nullable
    public static Material getMaterial(JsonElement element) {
        if (element != null) {
            return materials.get(element.getAsString());
        }
        return null;
    }

    /**
     * Gets the used Material of a ModuleInstnace
     *
     * @param instance
     * @return
     */
    @Nullable
    public static Material getMaterial(ItemModule.ModuleInstance instance) {
        JsonElement element = instance.getProperties().get(property);
        if (element != null) {
            return materials.get(element.getAsString()).getMaterial(instance);
        }
        return null;
    }

    /**
     * Sets a material of a MOduleinstance via Stringkey
     *
     * @param instance
     * @param material
     */
    public static void setMaterial(ItemModule.ModuleInstance instance, String material) {
        String propertyString = instance.moduleData.computeIfAbsent("properties", (key) -> {
            return "{material:empty}";
        });
        JsonObject moduleJson = Miapi.gson.fromJson(propertyString, JsonObject.class);
        moduleJson.addProperty(KEY, material);
        instance.moduleData.put("properties", Miapi.gson.toJson(moduleJson));
    }
}
