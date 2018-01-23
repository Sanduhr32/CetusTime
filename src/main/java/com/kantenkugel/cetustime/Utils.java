package com.kantenkugel.cetustime;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.MessageEmbed;

import java.awt.Color;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
        EB.addField(currentVoid.name, (currentVoid.active ? "geht in " : "kommt in ") + getDiffString(currentVoid.switchTime, true), false);
        if(currentVoid.active || ZonedDateTime.now().until(currentVoid.switchTime, ChronoUnit.DAYS) == 0) {
            EB.addField("Relay", currentVoid.location, false);
            if(!currentVoid.inventory.isEmpty())
                createInventory(currentVoid.inventory);
        }
        return EB.build();
    }

    public static String getDiffString(ZonedDateTime target) {
        return getDiffString(target, false);
    }

    public static String getDiffString(ZonedDateTime target, boolean showUnknown) {
        int until = (int) ZonedDateTime.now().until(target, ChronoUnit.SECONDS);

        if(until < Main.VALID_TILL && showUnknown)
            return "?";

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

    private static void createInventory(List<WarframeApi.VoidTrader.Item> inventory) {
        StringBuilder nameBuilder = new StringBuilder();
        StringBuilder priceBuilder = new StringBuilder();
        inventory.forEach(item -> {
            nameBuilder.append(item.name).append('\n');
            priceBuilder.append(String.format("`%-3dd`, `%,-7dcr`\n", item.ducatCost, item.creditCost));
        });
        nameBuilder.setLength(nameBuilder.length() - 1);
        priceBuilder.setLength(priceBuilder.length() - 1);
        EB.addField("Inventar", nameBuilder.toString(), true);
        EB.addField("Preis", priceBuilder.toString(), true);
    }
}
