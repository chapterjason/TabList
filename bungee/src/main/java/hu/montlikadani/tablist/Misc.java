package hu.montlikadani.tablist;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import hu.montlikadani.tablist.config.ConfigConstants;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public final class Misc {

	public static final BaseComponent EMPTY_COMPONENT = new net.md_5.bungee.api.chat.TextComponent();
	public static final BaseComponent[] EMPTY_COMPONENT_ARRAY = {};

	private static final String MAX_PLAYERS;

	static {
		java.util.Collection<net.md_5.bungee.api.config.ListenerInfo> coll = ProxyServer.getInstance()
				.getConfigurationAdapter().getListeners();

		MAX_PLAYERS = coll.isEmpty() ? "0" : Integer.toString(coll.iterator().next().getMaxPlayers());
	}

	public static String colorMsg(String s) {
		if (s.indexOf("#") >= 0) {
			s = Global.matchHexColour(s);
		}

		return ChatColor.translateAlternateColorCodes('&', s);
	}

	public static void sendMessage(net.md_5.bungee.api.CommandSender sender, ConfigConstants.MessageKeys key) {
		BaseComponent component = ConfigConstants.getMessage(key);

		if (component != EMPTY_COMPONENT) {
			sender.sendMessage(component);
		}
	}

	public static BaseComponent getComponentOfText(String s) {
		return new ComponentBuilder(s).getComponent(0);
	}

	@SuppressWarnings("deprecation")
	public static String replaceVariables(String str, ProxiedPlayer p) {
		// TODO Remove or make more customisable variables
		for (java.util.Map.Entry<String, String> map : ConfigConstants.CUSTOM_VARIABLES.entrySet()) {
			str = str.replace(map.getKey(), map.getValue());
		}

		if (ConfigConstants.getTimeFormat() != null && str.indexOf("%time%") >= 0) {
			str = str.replace("%time%", getTimeAsString(ConfigConstants.getTimeFormat()));
		}

		if (ConfigConstants.getDateFormat() != null && str.indexOf("%date%") >= 0) {
			str = str.replace("%date%", getTimeAsString(ConfigConstants.getDateFormat()));
		}

		net.md_5.bungee.api.connection.Server server = p.getServer();
		ServerInfo info = server != null ? server.getInfo() : null;

		if (info != null) {
			str = str.replace("%server%", info.getName());
			str = str.replace("%server-online%", Integer.toString(info.getPlayers().size()));
			str = str.replace("%bungee-motd%", info.getMotd());
		}

		if (str.indexOf("%ip%") >= 0) {
			InetSocketAddress address = null;
			SocketAddress sAddress = null;

			try {
				address = p.getAddress();
			} catch (Exception e) {
				sAddress = p.getSocketAddress();
			}

			str = str.replace("%ip%", address != null ? address.getAddress().getHostAddress()
					: sAddress != null ? sAddress.toString() : "");
		}

		str = str.replace("%max-players%", MAX_PLAYERS);
		str = str.replace("%player-name%", p.getName());
		str = str.replace("%display-name%", p.getDisplayName());
		str = str.replace("%bungee-online%", Integer.toString(ProxyServer.getInstance().getOnlineCount()));

		if (str.indexOf("%ping%") >= 0)
			str = str.replace("%ping%", Integer.toString(p.getPing()));

		Runtime runtime = Runtime.getRuntime();

		if (str.indexOf("%ram-used%") >= 0)
			str = str.replace("%ram-used%", Long.toString((runtime.totalMemory() - runtime.freeMemory()) / 1048576L));

		if (str.indexOf("%ram-max%") >= 0)
			str = str.replace("%ram-max%", Long.toString(runtime.maxMemory() / 1048576L));

		if (str.indexOf("%ram-free%") >= 0)
			str = str.replace("%ram-free%", Long.toString(runtime.freeMemory() / 1048576L));

		if (str.indexOf("%player-uuid%") >= 0)
			str = str.replace("%player-uuid%", p.getUniqueId().toString());

		if (str.indexOf("%player-language%") >= 0 || str.indexOf("%player-country%") >= 0) {
			java.util.Locale locale = p.getLocale();

			str = str.replace("%player-language%", locale == null ? "unknown" : locale.getDisplayLanguage());
			str = str.replace("%player-country%", locale == null ? "unknown" : locale.getDisplayCountry());
		}

		return str;
	}

	private static String getTimeAsString(DateTimeFormatter formatterPattern) {
		TimeZone zone = ConfigConstants.isUseSystemZone() ? TimeZone.getTimeZone(java.time.ZoneId.systemDefault())
				: TimeZone.getTimeZone(ConfigConstants.getTimeZone());
		LocalDateTime now = zone == null ? LocalDateTime.now() : LocalDateTime.now(zone.toZoneId());

		return now.format(formatterPattern);
	}
}