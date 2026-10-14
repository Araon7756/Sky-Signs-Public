package cn.skysigns.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Guards the shared static geometry; inventory adds only its display needle. */
final class ModelUiAssertions {
    private ModelUiAssertions() {}

    static void verifyResources() {
        JsonObject meter = model("block/barometer");
        JsonObject inventory = model("block/barometer_inventory");
        var body = meter.getAsJsonArray("elements");
        var preview = inventory.getAsJsonArray("elements");
        require(preview.size() == body.size() + 1, "Inventory must add only a static needle");
        for (int i = 0; i < body.size(); i++) {
            require(body.get(i).equals(preview.get(i)), "Inventory/body geometry diverged at " + i);
        }
        require(meter.get("display").equals(inventory.get("display")), "Display transforms diverged");
        require(model("block/hygrometer").get("parent").getAsString().equals("skysigns:block/barometer"),
                "Hygrometer must share the meter body");
        require(model("block/hygrometer_inventory").get("parent").getAsString()
                        .equals("skysigns:block/barometer_inventory"), "Hygrometer preview must share inventory body");
        require(model("item/weather_station").get("parent").getAsString()
                        .equals("skysigns:block/weather_station"), "Station item must use the world model");
        var rotation = model("block/weather_station").getAsJsonObject("display")
                .getAsJsonObject("gui").getAsJsonArray("rotation");
        require(rotation.get(1).getAsInt() == 45, "Station inventory must expose its south-facing screen");
        for (String name : new String[]{"block/barometer", "block/barometer_inventory", "block/weather_station"}) {
            for (var element : model(name).getAsJsonArray("elements")) {
                var from = element.getAsJsonObject().getAsJsonArray("from");
                var to = element.getAsJsonObject().getAsJsonArray("to");
                for (int axis = 0; axis < 3; axis++) {
                    require(from.get(axis).getAsDouble() >= 0 && to.get(axis).getAsDouble() <= 16
                                    && from.get(axis).getAsDouble() < to.get(axis).getAsDouble(),
                            "Invalid or out-of-block model element in " + name);
                }
            }
        }
    }

    private static JsonObject model(String name) {
        String path = "assets/skysigns/models/" + name + ".json";
        var stream = ModelUiAssertions.class.getClassLoader().getResourceAsStream(path);
        require(stream != null, "Missing model " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not read " + path, exception);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
