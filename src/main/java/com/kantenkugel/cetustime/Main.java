package com.kantenkugel.cetustime;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.utils.JDALogger;
import net.dv8tion.jda.core.utils.MiscUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    public static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    static JDA JDA;
    static final int VALID_TILL = -30;
    private static Map<String, Long> channelMsgMap = new HashMap<>();

    //Starting up with 30mib of memory should be sufficient (-Xmx30M)
    public static void main(String[] args) {
        try {
            new JDABuilder(AccountType.BOT).setToken(Config.BOT_TOKEN).addEventListener(new Listener()).buildAsync();
        } catch(LoginException e) {
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
        long updateIntervalMs = TimeUnit.MILLISECONDS.convert(Config.UPDATE_INTERVAL, Config.UPDATE_UNIT);
        //start with nice numbers... 500ms correction to notslip into next s frame
        long initialDelayMs = updateIntervalMs - (System.currentTimeMillis() % updateIntervalMs) - 500;
        EXECUTOR.scheduleAtFixedRate(Main::update, initialDelayMs, updateIntervalMs, TimeUnit.MILLISECONDS);
        setup = true;
    }

    private static void update() {
        try {
            if(!isValid()) {
                WarframeApi.fetch();
            }
            printInfo();
        } catch(Throwable throwable) {
            //otherwise they will just get eaten by executor, stopping scheduled task
            throwable.printStackTrace();
        }
    }

    private static boolean isValid() {
        ZonedDateTime now = ZonedDateTime.now();
        return WarframeApi.getCurrentCycle() != null
                && now.until(WarframeApi.getCurrentCycle().switchTime, ChronoUnit.SECONDS) >= VALID_TILL
                && now.until(WarframeApi.getCurrentTrader().switchTime, ChronoUnit.SECONDS) >= VALID_TILL;
    }

    private static void printInfo() {
        MessageEmbed embed = Utils.getEmbed();
        if(embed == null)
            return;
        channelMsgMap.forEach((key, value) -> {
            TextChannel tc = JDA.getTextChannelById(key);
            if(value == 0L || (Config.RENEW_INTERVAL > 0 &&
                    MiscUtil.getCreationTime(value).until(OffsetDateTime.now(), Config.RENEW_UNIT)
                            >= Config.RENEW_INTERVAL)) {
                if(value != 0L) {
                    tc.deleteMessageById(value).queue();
                    LOG.debug("Forced renew in channel {}", tc);
                }
                channelMsgMap.put(key, tc.sendMessage(embed).complete().getIdLong());
            } else {
                tc.getMessageById(value).queue((msg) -> cycleInfo(embed, tc, msg));
            }
        });
        LOG.trace("Current memory stats (in kib): Total: {}, Free: {}, Usage: {}",
                JDALogger.getLazyString(() -> Long.toString(Runtime.getRuntime().totalMemory()/1024)),
                JDALogger.getLazyString(() -> Long.toString(Runtime.getRuntime().freeMemory()/1024)),
                JDALogger.getLazyString(() -> Long.toString((Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1024))
        );
    }

    private static void cycleInfo(MessageEmbed embed, TextChannel tc, Message msg) {
        long value = msg.getIdLong();
        String oldContent = msg.getContentRaw();
        WarframeApi.CetusCycle currentCycle = WarframeApi.getCurrentCycle();
        MessageBuilder builder = new MessageBuilder().setEmbed(embed);
        if (!currentCycle.isDay && oldContent.isEmpty()) {
            tc.getGuild().getRolesByName("Eidolon Hunter", true).stream().findFirst().ifPresent(builder::append);
            tc.sendMessage(builder.build()).queue();
            tc.deleteMessageById(value).queue();
        } else {
            tc.editMessageById(value, builder.setContent("").build()).override(true).queue();
        }
    }
}
