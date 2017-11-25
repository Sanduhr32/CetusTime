package com.kantenkugel.cetustime;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

public class Config {
    private static final Logger LOG = LoggerFactory.getLogger(Config.class);

    private static final Path CFG_PATH = Paths.get("config.json");
    private static final URL CFG_TPL_PATH;

    private static final String PUBLIC_TEMPLATE_URL = "https://github.com/kantenkugel/CetusTime/blob/master/src/main/resources/configTemplate.json";

    private static JSONObject rawCfg;

    public static final long UPDATE_INTERVAL;
    public static final TimeUnit UPDATE_UNIT;
    //forces a re-creation of new message after this amount of time... 0 to disable
    public static final long RENEW_INTERVAL;
    public static final ChronoUnit RENEW_UNIT;
    public static final String BOT_TOKEN;
    public static final boolean KEEP_ON_BOTTOM;

    private static List<String> channelIds, adminIds;

    public static List<String> getChannelIds() {
        return Collections.unmodifiableList(channelIds);
    }

    public static List<String> getAdminIds() {
        return Collections.unmodifiableList(adminIds);
    }

    public static void addChannel(String channelId) {
        if(channelIds.contains(channelId))
            return;
        channelIds.add(channelId);
        rawCfg.put("textChannelIds", channelIds);
    }

    public static void removeChannel(String channelId) {
        if(!channelIds.contains(channelId))
            return;
        channelIds.remove(channelId);
        rawCfg.put("textChannelIds", channelIds);
    }

    public static void write() {
        try {
            Files.write(CFG_PATH, rawCfg.toString(2).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch(IOException e) {
            LOG.error("Could not write new config file", e);
        }
    }

    //Static setup (read+assign)
    static {
        //SETUP FOR TEMPLATE PATH
        CFG_TPL_PATH = Config.class.getClassLoader().getResource("configTemplate.json");
        if(CFG_TPL_PATH == null)
            throw new RuntimeException("Could not get template config");

        //READING
        //temp vars to set constants
        long updateInterval = 10L;
        TimeUnit updateUnit = TimeUnit.SECONDS;
        long renewInterval = 0L;
        ChronoUnit renewUnit = ChronoUnit.HOURS;
        String botToken = "";
        boolean keepOnBottom = true;

        adminIds = new ArrayList<>();
        channelIds = new ArrayList<>();

        if(!Files.exists(CFG_PATH)) {
            try {
                Files.copy(CFG_TPL_PATH.openStream(), CFG_PATH, StandardCopyOption.REPLACE_EXISTING);
            } catch(IOException e) {
                LOG.error("Could not copy config template into root directory", e);
            }
            LOG.warn("Config file didn't exist. Template was created. Please fill out template and restart.");
            System.exit(0);
        } else {
            try {
                //reading from json... keeping read in chronological order of config addition
                rawCfg = new JSONObject(new String(Files.readAllBytes(CFG_PATH)));
                botToken = rawCfg.getString("botToken");
                StreamSupport.stream(rawCfg.getJSONArray("adminIds").spliterator(), false)
                        .map(String::valueOf)
                        .forEach(adminIds::add);
                StreamSupport.stream(rawCfg.getJSONArray("textChannelIds").spliterator(), false)
                        .map(String::valueOf)
                        .forEach(channelIds::add);
                keepOnBottom = rawCfg.getBoolean("keepOnBottom");

                JSONObject times = rawCfg.getJSONObject("times");

                JSONObject updateTimes = times.getJSONObject("update");
                updateInterval = updateTimes.getLong("interval");
                updateUnit = TimeUnit.valueOf(updateTimes.getString("unit").toUpperCase());

                JSONObject renewTimes = times.getJSONObject("renew");
                renewInterval = renewTimes.getLong("interval");
                renewUnit = ChronoUnit.valueOf(renewTimes.getString("unit").toUpperCase());
            } catch(Exception ex) {
                LOG.error("Could not read (full) config from file. The config format may have changed. " +
                        "For a reference config file see {}", PUBLIC_TEMPLATE_URL, ex);
                System.exit(1);
            }
        }

        //setting constants from temp-vars
        UPDATE_INTERVAL = updateInterval;
        UPDATE_UNIT = updateUnit;
        RENEW_INTERVAL = renewInterval;
        RENEW_UNIT = renewUnit;
        BOT_TOKEN = botToken;
        KEEP_ON_BOTTOM = keepOnBottom;

    }
}
