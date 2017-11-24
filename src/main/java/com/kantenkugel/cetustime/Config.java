package com.kantenkugel.cetustime;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Config {
    private static final Logger LOG = LoggerFactory.getLogger(Config.class);

    private static final Path CFG_PATH = Paths.get("config.json");
    private static final Path CFG_TPL_PATH;

    private static JSONObject rawCfg;
    private static String botToken;
    private static List<String> channelIds, adminIds;
    private static boolean keepOnBottom;

    public static String getBotToken() {
        return botToken;
    }

    public static List<String> getChannelIds() {
        return channelIds;
    }

    public static List<String> getAdminIds() {
        return adminIds;
    }

    public static boolean isKeepOnBottom() {
        return keepOnBottom;
    }

    public static void addChannel(String channelId) {
        if(channelIds.contains(channelId))
            return;
        channelIds.add(channelId);
        rawCfg.put("channelIds", channelIds);
    }

    public static void removeChannel(String channelId) {
        if(!channelIds.contains(channelId))
            return;
        channelIds.remove(channelId);
        rawCfg.put("channelIds", channelIds);
    }

    static {
        Path tmp = null;
        try {
            tmp = Paths.get(Config.class.getClassLoader().getResource("configTemplate.json").toURI());
        } catch(URISyntaxException e) {
            LOG.error("Could not get configTemplate from resources", e);
            System.exit(1);
        }
        CFG_TPL_PATH = tmp;

        init();
    }

    private static void init() {
        if(!Files.exists(CFG_PATH)) {
            try {
                Files.copy(CFG_TPL_PATH, CFG_PATH, StandardCopyOption.REPLACE_EXISTING);
            } catch(IOException e) {
                LOG.error("Could not copy config template into root directory", e);
            }
            LOG.warn("Config file didn't exist. Template was created. Please fill out template and restart.");
            System.exit(0);
        }
        try {
            rawCfg = new JSONObject(new String(Files.readAllBytes(CFG_PATH)));
            botToken = rawCfg.getString("botToken");
            adminIds = StreamSupport.stream(rawCfg.getJSONArray("adminIds").spliterator(), false)
                    .map(String::valueOf).collect(Collectors.toList());
            channelIds = StreamSupport.stream(rawCfg.getJSONArray("textChannelIds").spliterator(), false)
                    .map(String::valueOf).collect(Collectors.toList());
            keepOnBottom = rawCfg.getBoolean("keepOnBottom");
        } catch(Exception ex) {
            LOG.error("Could not read config from file", ex);
            System.exit(1);
        }
    }

    public static void write() {
        try {
            Files.write(CFG_PATH, rawCfg.toString(4).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch(IOException e) {
            LOG.error("Could not write new config file", e);
        }
    }
}
