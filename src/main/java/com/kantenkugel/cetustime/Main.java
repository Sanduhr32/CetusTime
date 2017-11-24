package com.kantenkugel.cetustime;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.utils.JDALogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
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
        EXECUTOR.scheduleAtFixedRate(Main::update, 0, 2, TimeUnit.SECONDS);
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
            if(value == 0L) {
                channelMsgMap.put(key, JDA.getTextChannelById(key).sendMessage(embed).complete().getIdLong());
            } else {
                JDA.getTextChannelById(key).editMessageById(value, embed).queue();
            }
        });
        LOG.trace("Current memory stats (in kib): Total: {}, Free: {}, Usage: {}",
                JDALogger.getLazyString(() -> Long.toString(Runtime.getRuntime().totalMemory()/1024)),
                JDALogger.getLazyString(() -> Long.toString(Runtime.getRuntime().freeMemory()/1024)),
                JDALogger.getLazyString(() -> Long.toString((Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1024))
        );
    }
}
