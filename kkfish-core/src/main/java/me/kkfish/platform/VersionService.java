package me.kkfish.platform;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;

import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;

/**
 * 统一的服务器版本检测服务。
 *
 * <p>合并 kkfish.java、NBTUtil、XSeriesUtil、Metrics 中分散的版本检测逻辑，
 * 提供单一入口查询主版本号、次版本号、NMS 版本字符串、Folia 支持状态。</p>
 */
public class VersionService {

    private final int majorVersion;
    private final int minorVersion;
    private final String nmsVersion;
    private final boolean foliaSupported;

    public VersionService() {
        this.majorVersion = detectMajorVersion();
        this.minorVersion = detectMinorVersion();
        this.nmsVersion = detectNmsVersion();
        this.foliaSupported = detectFolia();
    }

    /**
     * 从 Bukkit.getVersion() 解析主版本号。
     */
    private int detectMajorVersion() {
        Matcher matcher = matchVersion();
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /**
     * 从 Bukkit.getVersion() 解析次版本号。
     */
    private int detectMinorVersion() {
        Matcher matcher = matchVersion();
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private Matcher matchVersion() {
        String version = Bukkit.getVersion();
        Pattern pattern = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
        return pattern.matcher(version);
    }

    /**
     * 获取 NMS 版本字符串（如 v1_16_R3）。
     *
     * <p>通过 Bukkit.getServer().getClass().getPackage().getName() 解析。</p>
     *
     * @return NMS 版本字符串，无法识别时返回空字符串
     */
    private String detectNmsVersion() {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String[] parts = packageName.split("\\.");
            if (parts.length > 0) {
                return parts[parts.length - 1];
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * 检测当前服务器是否为 Folia。
     */
    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 输出版本检测结果到控制台。
     */
    public void logDetection(kkfish plugin) {
        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.version_detected",
                "Detected server version: " + majorVersion + "." + minorVersion, majorVersion, minorVersion));
        if (foliaSupported) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.folia_detected",
                    "Folia detected: region/entity/global schedulers will be used."));
        }
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    /**
     * @return NMS 版本字符串（如 v1_16_R3），无法识别时返回空字符串
     */
    public String getNmsVersion() {
        return nmsVersion;
    }

    /**
     * @return 当前服务器是否为 Folia
     */
    public boolean isFolia() {
        return foliaSupported;
    }

    /**
     * @return 是否为 1.21 或更高版本
     */
    public boolean is1_21OrHigher() {
        return (majorVersion > 1) || (majorVersion == 1 && minorVersion >= 21);
    }

    /**
     * @return 是否为 1.14 或更高版本（PDC 可用）
     */
    public boolean is1_14OrHigher() {
        return (majorVersion > 1) || (majorVersion == 1 && minorVersion >= 14);
    }
}
