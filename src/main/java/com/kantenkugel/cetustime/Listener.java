package com.kantenkugel.cetustime;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.ISnowflake;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ReconnectedEvent;
import net.dv8tion.jda.core.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.kantenkugel.cetustime.Main.LOG;

public class Listener extends ListenerAdapter {

    private void setup(Event event) {
        JDA jda = event.getJDA();
        Main.JDA = jda;
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
                if(m.getAuthor() == jda.getSelfUser() && m.getEmbeds().size() > 0) {
                    botMsg.set(m);
                    return false;
                }
                return (prevMsgs.incrementAndGet() < 200);
            });

            Message msg = botMsg.get();

            if(msg == null || prevMsgs.get() > 0) {
                LOG.debug("Scanned {} messages", prevMsgs.get());
                if(msg != null && prevMsgs.get() > 0) {
                    LOG.info("Deleting outdated msg in {}...", channelId);
                    msg.delete().queue();
                    msg = null;
                } else {
                    LOG.info("Creating new message for tc {}...", channelId);
                }
            }
            Main.getMap().put(channelId, msg == null ? 0L : msg.getIdLong());
        }
        if(idsToDelete.size() > 0) {
            idsToDelete.forEach(Config::removeChannel);
            Config.write();
            LOG.info("Cleaned up {} old TCs", idsToDelete.size());
        }
        LOG.info("Done with setup... starting up scheduled task");
        Main.setupExecutor();
    }

    @Override
    public void onReady(ReadyEvent event) {
        setup(event);
    }

    @Override
    public void onReconnect(ReconnectedEvent event) {
        setup(event);
    }

    @Override
    public void onPrivateMessageReceived(PrivateMessageReceivedEvent e) {
        if(Config.getAdminIds().contains(e.getAuthor().getId()) && e.getMessage().getContent().equals("shutdown")) {
            LOG.info("Admin {} issued shutdown... ", e.getAuthor());
            e.getJDA().shutdown();
        }
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent e) {
        if(e.getAuthor().isBot() || e.getAuthor().isFake())
            return;

        Map<String, Long> map = Main.getMap();

        //handle commands
        if(e.getMessage().getRawContent().equals(e.getJDA().getSelfUser().getAsMention() + " track")) {
            String chanId = e.getChannel().getId();
            if(map.containsKey(chanId)) {
                long msgId = map.remove(chanId);
                Config.removeChannel(chanId);
                Config.write();
                e.getChannel().deleteMessageById(msgId).queue();
                e.getChannel().sendMessage("No longer tracking here...").queue();
                LOG.info("No longer tracking channel {}", e.getChannel());
            } else {
                map.put(chanId, 0L);
                Config.addChannel(chanId);
                Config.write();
                LOG.info("Now tracking channel {}", e.getChannel());
            }
        }
        //TODO: handle other commands

        //handle keepOnBottom
        TextChannel channel = e.getChannel();
        if(Config.KEEP_ON_BOTTOM && Config.getChannelIds().contains(channel.getId())) {
            if(map.get(channel.getId()) == 0L)
                return;
            LOG.debug("Renewing message in tc {}", channel);
            map.compute(channel.getId(), (key, value) -> {
                if(value != 0L)
                    channel.deleteMessageById(value).queueAfter(Config.UPDATE_INTERVAL, Config.UPDATE_UNIT);
                return 0L;
            });
        }
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent e) {
        Map<String, Long> map = Main.getMap();
        Set<String> currIds = map.keySet();
        List<String> idsToRemove = e.getGuild().getTextChannels().stream()
                .map(ISnowflake::getId)
                .filter(currIds::contains)
                .collect(Collectors.toList());
        idsToRemove.forEach(id -> {
            map.remove(id);
            Config.removeChannel(id);
        });
        if(idsToRemove.size() > 0) {
            Config.write();
            LOG.info("Left guild {} and removed {} tracking channels", e.getGuild(), idsToRemove.size());
        }
    }

    @Override
    public void onTextChannelDelete(TextChannelDeleteEvent event) {
        TextChannel tc = event.getChannel();
        String id = tc.getId();
        Map<String, Long> map = Main.getMap();
        if(map.containsKey(id)) {
            map.remove(id);
            Config.removeChannel(id);
            Config.write();
            LOG.info("Tracking channel {} was deleted and therefore is no longer tracked", tc);
        }
    }
}
