package com.kantenkugel.cetustime;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;

import java.io.IOException;
import java.time.ZonedDateTime;

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
                    currentTrader = new VoidTrader(voidActive, voidSwitch, voidTrader.getString("location"), voidTrader.getString("character"));
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

        private VoidTrader(boolean active, ZonedDateTime switchTime, String location, String name) {
            this.active = active;
            this.switchTime = switchTime;
            this.location = location;
            this.name = name;
        }
    }
}
