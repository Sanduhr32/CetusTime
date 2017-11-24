package com.kantenkugel.cetustime;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.ISnowflake;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
        channelMsgMap.forEach((key, value) -> {
            if(value == 0L) {
                channelMsgMap.put(key, jda.getTextChannelById(key).sendMessage(embed).complete().getIdLong());
            } else {
                jda.getTextChannelById(key).editMessageById(value, embed).queue();
            }
        });
    }

    @Override
    public void onEvent(Event event) {
        if(event instanceof ReadyEvent) {
            jda = event.getJDA();
            LOG.info("Initializing messages");
            List<String> idsToDelete = new ArrayList<>(Config.getChannelIds().size());
            for(String channelId : Config.getChannelIds()) {
                TextChannel tc = jda.getTextChannelById(channelId);
                if(tc == null) {
                    idsToDelete.add(channelId);
                    continue;
                }

                AtomicReference<Message> botMsg = new AtomicReference<>(null);
                AtomicInteger prevMsgs = new AtomicInteger(0);
                tc.getIterableHistory().cache(false).forEachRemaining(m -> {
                    if(m.getAuthor() == jda.getSelfUser()) {
                        botMsg.set(m);
                        return false;
                    }
                    return (prevMsgs.incrementAndGet() < 200);
                });

                Message msg = botMsg.get();

                if(msg == null || prevMsgs.get() > 0) {
                    LOG.trace("Scanned {} messages", prevMsgs.get());
                    if(msg != null && prevMsgs.get() > 0) {
                        LOG.info("Deleting outdated msg in {}...", channelId);
                        msg.delete().queue();
                        msg = null;
                    } else {
                        LOG.info("Creating new message for tc {}...", channelId);
                    }
                }
                channelMsgMap.put(channelId, msg == null ? 0L : msg.getIdLong());
            }
            if(idsToDelete.size() > 0) {
                idsToDelete.forEach(Config::removeChannel);
                Config.write();
                LOG.info("Cleaned up {} old TCs", idsToDelete.size());
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
                if(e.getMessage().getRawContent().equals(e.getJDA().getSelfUser().getAsMention() + " track")) {
                    String chanId = e.getChannel().getId();
                    if(channelMsgMap.containsKey(chanId)) {
                        channelMsgMap.remove(chanId);
                        Config.removeChannel(chanId);
                        Config.write();
                        e.getChannel().sendMessage("No longer tracking here...").queue();
                        LOG.info("No longer tracking channel {} ({})", e.getChannel(), chanId);
                    } else {
                        channelMsgMap.put(chanId, 0L);
                        Config.addChannel(chanId);
                        Config.write();
                        LOG.info("Now tracking channel {} ({})", e.getChannel(), chanId);
                    }
                }
                //TODO: handle special commands
            }
            TextChannel channel = e.getChannel();
            if(Config.isKeepOnBottom() && Config.getChannelIds().contains(channel.getId())) {
                if(channelMsgMap.get(channel.getId()) == 0L)
                    return;
                LOG.trace("Renewing message in tc {}", channel.getIdLong());
                channelMsgMap.compute(channel.getId(), (key, value) -> {
                    if(value != 0L)
                        channel.deleteMessageById(value).queueAfter(1, TimeUnit.SECONDS);
                    return 0L;
                });
            }
        } else if(event instanceof GuildLeaveEvent) {
            GuildLeaveEvent e = (GuildLeaveEvent) event;
            Set<String> currIds = channelMsgMap.keySet();
            List<String> idsToRemove = e.getGuild().getTextChannels().stream()
                    .map(ISnowflake::getId)
                    .filter(currIds::contains)
                    .collect(Collectors.toList());
            idsToRemove.forEach(id -> {
                channelMsgMap.remove(id);
                Config.removeChannel(id);
            });
            if(idsToRemove.size() > 0) {
                Config.write();
                LOG.info("Left guild {} and removed {} tracking channels", e.getGuild().getName(), idsToRemove.size());
            }
        } else if(event instanceof TextChannelDeleteEvent) {
            TextChannel tc = ((TextChannelDeleteEvent) event).getChannel();
            String id = tc.getId();
            if(channelMsgMap.containsKey(id)) {
                channelMsgMap.remove(id);
                Config.removeChannel(id);
                Config.write();
                LOG.info("Tracking channel {} ({}) was deleted and therefore is no longer tracked", tc.getName(), id);
            }
        }
    }
}
