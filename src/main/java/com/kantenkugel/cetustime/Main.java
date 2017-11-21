package com.kantenkugel.cetustime;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main implements EventListener {
    public static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private static JDA jda;
    private static Map<String, Long> channelMsgMap = new HashMap<>();

    public static void main(String[] args) {
        try {
            new JDABuilder(AccountType.BOT).setToken(Config.getBotToken()).addEventListener(new Main()).buildAsync();
        } catch(LoginException | RateLimitedException e) {
            e.printStackTrace();
        }
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
        channelMsgMap.forEach((key, value) -> jda.getTextChannelById(key).editMessageById(value, embed).queue());
    }

    @Override
    public void onEvent(Event event) {
        if(event instanceof ReadyEvent) {
            jda = event.getJDA();
            LOG.info("Initializing messages");
            for(String channelId : Config.getChannelIds()) {
                List<Message> msgsToDelete = new ArrayList<>(100);
                Message botMsg = null;
                TextChannel tc = jda.getTextChannelById(channelId);
                for(Message msg : tc.getHistory().retrievePast(100).complete()) {
                    if(msg.getAuthor() == jda.getSelfUser()) {
                        botMsg = msg;
                        break;
                    }
                    msgsToDelete.add(msg);
                }
                if(botMsg == null) {
                    LOG.info("Creating new message for tc {}...", channelId);
                    botMsg = tc.sendMessage("Setting up...").complete();
                } else if(msgsToDelete.size() > 0 && Config.isDeleteOtherMessages()) {
                    tc.deleteMessages(msgsToDelete).queue();
                    LOG.info("Deleted {} messages from channelid {}", msgsToDelete.size(), channelId);
                }
                channelMsgMap.put(channelId, botMsg.getIdLong());
            }
            LOG.info("Done with setup... starting up scheduled task");
            EXECUTOR.scheduleAtFixedRate(Main::update, 0, 2, TimeUnit.SECONDS);
        } else if(event instanceof PrivateMessageReceivedEvent) {
            PrivateMessageReceivedEvent e = (PrivateMessageReceivedEvent) event;
            if(Config.getAdminIds().contains(e.getAuthor().getId()) && e.getMessage().getContent().equals("shutdown")) {
                LOG.info("Admin {} ({}) issued shutdown... ", e.getAuthor().getName(), e.getAuthor().getIdLong());
                jda.shutdown();
            }
        } else if(event instanceof GuildMessageReceivedEvent) {
            GuildMessageReceivedEvent e = (GuildMessageReceivedEvent) event;
            if(e.getAuthor().isBot() || e.getAuthor().isFake())
                return;
            if(Config.getAdminIds().contains(e.getAuthor().getId())) {
                //TODO: handle special commands
            }
            if(Config.isDeleteOtherMessages() && Config.getChannelIds().contains(e.getChannel().getId())) {
                LOG.info("Deleted message of {} in tc {}", e.getAuthor().getIdLong(), e.getChannel().getIdLong());
                e.getMessage().delete().queueAfter(1, TimeUnit.SECONDS, v -> {}, err -> {});
            }
        }
    }
}
