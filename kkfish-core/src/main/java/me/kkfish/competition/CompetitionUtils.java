package me.kkfish.competition;

import me.kkfish.misc.MessageManager;

public class CompetitionUtils {

    public static String formatDuration(int seconds, MessageManager messageManager) {
        int days = seconds / 86400;
        int hours = (seconds % 86400) / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(messageManager.getMessageWithoutPrefix("time.day", "天"));
        }
        if (hours > 0) {
            sb.append(hours).append(messageManager.getMessageWithoutPrefix("time.hour", "时"));
        }
        if (minutes > 0) {
            sb.append(minutes).append(messageManager.getMessageWithoutPrefix("time.minute", "分"));
        }
        if (secs > 0 || sb.length() == 0) {
            sb.append(secs).append(messageManager.getMessageWithoutPrefix("time.second", "秒"));
        }

        return sb.toString();
    }
}
