package com.kantenkugel.cetustime;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.utils.JDALogger;
import net.dv8tion.jda.core.utils.MiscUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    static JDA JDA;
    private static Map<String, Long> channelMsgMap = new HashMap<>();

    //Starting up with 30mib of memory should be sufficient (-Xmx30M)
    public static void main(String[] args) {
        try {
            new JDABuilder(AccountType.BOT).setToken(Config.getBotToken()).addEventListener(new Listener()).buildAsync();
        } catch(LoginException | RateLimitedException e) {
            LOG.error("Error starting up bot", e);
        }
    }

    static Map<String, Long> getMap() {
        return channelMsgMap;
    }

    private static boolean setup = false;
    static synchronized void setupExecutor() {
        if(setup)
            return;
        long updateIntervalMs = TimeUnit.MILLISECONDS.convert(Constants.UPDATE_INTERVAL, Constants.UPDATE_INTERVAL_UNIT);
        long initialDelayMs = updateIntervalMs - (System.currentTimeMillis() % updateIntervalMs);
        EXECUTOR.scheduleAtFixedRate(Main::update, initialDelayMs, updateIntervalMs, TimeUnit.MILLISECONDS);
        setup = true;
    }

    private static void update() {
        if(!isValid()) {
            WarframeApi.fetch();
        }
        printInfo();
    }

    private static boolean isValid() {
        ZonedDateTime now = ZonedDateTime.now();
        return WarframeApi.getCurrentCycle() != null
                && WarframeApi.getCurrentCycle().switchTime.isAfter(now)
                && WarframeApi.getCurrentTrader().switchTime.isAfter(now);
    }

    private static void printInfo() {
        MessageEmbed embed = Utils.getEmbed();
        if(embed == null)
            return;
        channelMsgMap.forEach((key, value) -> {
            TextChannel tc = JDA.getTextChannelById(key);
            if(value == 0L || (Constants.FORCED_RENEW_INTERVAL > 0 &&
                    MiscUtil.getCreationTime(value).until(OffsetDateTime.now(), Constants.FORCED_RENEW_INTERVAL_UNIT)
                            >= Constants.FORCED_RENEW_INTERVAL)) {
                if(value != 0L) {
                    tc.deleteMessageById(value).queue();
                    LOG.debug("Forced renew in channel {}", tc);
                }
                channelMsgMap.put(key, tc.sendMessage(embed).complete().getIdLong());
            } else {
                tc.editMessageById(value, embed).queue();
            }
        });
        LOG.trace("Current memory stats (in kib): Total: {}, Free: {}, Usage: {}",
                JDALogger.getLazyString(() -> Long.toString(Runtime.getRuntime().totalMemory()/1024)),
                JDALogger.getLazyString(() -> Long.toString(Runtime.getRuntime().freeMemory()/1024)),
                JDALogger.getLazyString(() -> Long.toString((Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1024))
        );
    }
}
