package com.kantenkugel.cetustime;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class WarframeApi {
    private static final String API_URL = "https://ws.warframestat.us/pc";
    private static final OkHttpClient CLIENT = new OkHttpClient();

    private static CetusCycle currentCycle;
    private static VoidTrader currentTrader;
    private static ZonedDateTime lastUpdate;

    public static void fetch() {
        try {
            Response execute = CLIENT.newCall(new Request.Builder().get().url(API_URL).build()).execute();
            if(execute.isSuccessful()) {
                try(ResponseBody body = execute.body()) {
                    if(body == null)
                        return;
                    JSONObject json = new JSONObject(body.string());
                    JSONObject cetusCycle = json.getJSONObject("cetusCycle");
                    currentCycle = new CetusCycle(cetusCycle.getBoolean("isDay"), ZonedDateTime.parse(cetusCycle.getString("expiry")));
                    JSONObject voidTrader = json.getJSONObject("voidTrader");
                    boolean voidActive = voidTrader.getBoolean("active");
                    ZonedDateTime voidSwitch = ZonedDateTime.parse(voidActive ? voidTrader.getString("expiry") : voidTrader.getString("activation"));
                    currentTrader = new VoidTrader(
                            voidActive,
                            voidSwitch,
                            voidTrader.getString("location"),
                            voidTrader.getString("character"),
                            voidTrader.has("inventory")
                                    ? VoidTrader.getInventory(voidTrader.getJSONArray("inventory"))
                                    : Collections.emptyList()
                    );
                    if(currentTrader.active && currentTrader.inventory.isEmpty())   //in case of missing inventory, schedule update in 30s
                        Main.EXECUTOR.schedule(WarframeApi::fetch, 30, TimeUnit.SECONDS);
                    lastUpdate = ZonedDateTime.now();
                }
            }
        } catch(IOException ignored) {}
    }

    public static CetusCycle getCurrentCycle() {
        return currentCycle;
    }

    public static VoidTrader getCurrentTrader() {
        return currentTrader;
    }

    public static ZonedDateTime getLastUpdate() {
        return lastUpdate;
    }

    public static class CetusCycle {
        public final boolean isDay;
        public final ZonedDateTime switchTime;

        private CetusCycle(boolean isDay, ZonedDateTime switchTime) {
            this.isDay = isDay;
            this.switchTime = switchTime;
        }
    }

    public static class VoidTrader {
        public final boolean active;
        public final ZonedDateTime switchTime;
        public final String location, name;
        public final List<Item> inventory;

        private VoidTrader(boolean active, ZonedDateTime switchTime, String location, String name, List<Item> inventory) {
            this.active = active;
            this.switchTime = switchTime;
            this.location = location;
            this.name = name;
            this.inventory = inventory;
        }

        private static List<Item> getInventory(JSONArray jsonArray) {
            List<Item> inv = jsonArray.toList().stream()
                    .map(o -> Item.from((HashMap<String, Object>) o))
                    .sorted(Comparator.comparing(item -> item.name))
                    .collect(Collectors.toList());
            return Collections.unmodifiableList(inv);
        }

        public static class Item {
            public final String name;
            public final int ducatCost, creditCost;

            private Item(String name, int ducatCost, int creditCost) {
                this.name = name;
                this.ducatCost = ducatCost;
                this.creditCost = creditCost;
            }

            private static Item from(HashMap<String, Object> map) {
                return new Item(map.get("item").toString(), (int) map.get("ducats"), (int) map.get("credits"));
            }
        }
    }
}
