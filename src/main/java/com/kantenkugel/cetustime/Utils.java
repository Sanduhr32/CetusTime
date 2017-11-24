package com.kantenkugel.cetustime;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.MessageEmbed;

import java.awt.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;

public class Utils {
    private static final EmbedBuilder EB = new EmbedBuilder();
    private static final Color DAY_COLOR = Color.ORANGE, NIGHT_COLOR = Color.BLUE;

    public static MessageEmbed getEmbed() {
        WarframeApi.CetusCycle currentCycle = WarframeApi.getCurrentCycle();
        WarframeApi.VoidTrader currentVoid = WarframeApi.getCurrentTrader();
        if(currentCycle == null || currentVoid == null)
            return null;

        EB.setColor(currentCycle.isDay ? DAY_COLOR : NIGHT_COLOR);
        EB.setFooter("Letztes API update", null);
        EB.setTimestamp(WarframeApi.getLastUpdate());
        EB.clearFields();
        EB.addField("Cetus Cyclus", currentCycle.isDay ? "Tag" : "Nacht", false);
        EB.addField("Zeit bis " + (currentCycle.isDay ? "Nacht" : "Tag"),
                String.format("%s%n(%s)",
                        getDiffString(currentCycle.switchTime),
                        currentCycle.switchTime.withZoneSameInstant(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM))
                ), false);
        EB.addField(currentVoid.name, (currentVoid.active ? "geht in " : "kommt in ") + getDiffString(currentVoid.switchTime), false);
        EB.addField(String.format("%s Relay", currentVoid.name), currentVoid.location, false);
        return EB.build();
    }

    public static String getDiffString(ZonedDateTime target) {
        int until = (int) ZonedDateTime.now().until(target, ChronoUnit.SECONDS);
        int days = until / 86400;
        int hours = (until % 86400) / 3600;
        int mins = (until % 3600) / 60;
        int secs = until % 60;
        StringBuilder sb = new StringBuilder();
        if(days > 0)
            sb.append(days).append("d ");
        if(hours > 0 || days > 0)
            sb.append(hours).append("h ");
        if(mins > 0 || hours > 0 || days > 0)
            sb.append(mins).append("m ");
        sb.append(secs).append("s");
        return sb.toString();
    }
}
