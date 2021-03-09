package hu.montlikadani.tablist.bukkit.tablist.entry.row;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import hu.montlikadani.tablist.bukkit.TabList;
import hu.montlikadani.tablist.bukkit.API.TabListAPI;
import hu.montlikadani.tablist.bukkit.tablist.entry.TabEntries;
import hu.montlikadani.tablist.bukkit.tablist.entry.row.variable.VariableReplacer;
import hu.montlikadani.tablist.bukkit.user.TabListUser;
import hu.montlikadani.tablist.bukkit.utils.Util;
import hu.montlikadani.tablist.bukkit.utils.reflection.ReflectionUtils;
import hu.montlikadani.tablist.bukkit.utils.ServerVersion;

@SuppressWarnings("unchecked")
public class RowPlayer implements IRowPlayer {

	public final TabEntries root;
	public final VariableReplacer replacer;

	public int rowIndex = 0, columnIndex = 0;

	private final TabList plugin = TabListAPI.getPlugin();

	private UUID skinId;
	private String text = " ";
	private Player player;
	private int ping;

	private Field infoList;
	private Constructor<?> playerInfoDataConstr, playOutPlayerInfoConstr;
	private Class<?> packetPlayOutPlayerInfo, enumPlayerInfoAction, entityPlayer;
	private Object packet, rowPlayer, gameMode;
	private GameProfile gameProfile;

	public RowPlayer(TabEntries root) {
		this.root = root;
		replacer = new VariableReplacer(this);

		try {
			packetPlayOutPlayerInfo = ReflectionUtils.getNMSClass("PacketPlayOutPlayerInfo");

			entityPlayer = ReflectionUtils.getNMSClass("EntityPlayer");
			infoList = ReflectionUtils.getField(packetPlayOutPlayerInfo, "b");
			enumPlayerInfoAction = ReflectionUtils.Classes.getEnumPlayerInfoAction(packetPlayOutPlayerInfo);

			Class<?> playerInfoData = null;
			try {
				playerInfoData = ReflectionUtils.getNMSClass("PacketPlayOutPlayerInfo$PlayerInfoData");
			} catch (ClassNotFoundException e) {
				playerInfoData = ReflectionUtils.getNMSClass("PlayerInfoData");
			}

			if (playerInfoData == null) {
				return;
			}

			Constructor<?>[] array = playerInfoData.getConstructors();
			for (Constructor<?> constr : array) {
				if (constr.getParameterCount() == 4 || constr.getParameterCount() == 5) {
					playerInfoDataConstr = constr;
					playerInfoDataConstr.setAccessible(true);
					break;
				}
			}

			playOutPlayerInfoConstr = packetPlayOutPlayerInfo.getDeclaredConstructor(enumPlayerInfoAction,
					Array.newInstance(entityPlayer, 0).getClass());
			playOutPlayerInfoConstr.setAccessible(true);

			Class<?> enumGameMode = ReflectionUtils.getNMSClass("EnumGamemode");
			if (enumGameMode == null) {
				enumGameMode = ReflectionUtils.getNMSClass("WorldSettings$EnumGamemode");
			}

			gameMode = enumGameMode.getDeclaredField("NOT_SET").get(enumGameMode);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public Optional<Player> asPlayer() {
		return Optional.ofNullable(player);
	}

	@Override
	public String getText() {
		return text;
	}

	/**
	 * Checks whenever this row text is empty
	 * 
	 * @return true if empty, otherwise false
	 */
	public final boolean isTextEmpty() {
		return text.trim().isEmpty();
	}

	/**
	 * Checks whenever the text is empty, skin is not set and another alive player
	 * is not exist. More specifically, this row text is empty, skin uuid is not set
	 * and player is also not set.
	 * 
	 * @return true if empty, otherwise false
	 */
	public final boolean isEmpty() {
		return skinId == null && player == null && isTextEmpty();
	}

	@Override
	public int getPingLatency() {
		return ping;
	}

	@Override
	public void setText(String text) {
		this.text = text == null ? " " : text;
	}

	@Override
	public void setPing(int ping) {
		this.ping = ping >= 0 ? ping : 20;
	}

	@Override
	public void setPlayer(Player player) {
		if (player != null) {
			remove();
		}

		this.player = player;

		if (player == null) {
			return;
		}

		// Need 3 tick delay to show player
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			try {
				String name = String.format("%03d", rowIndex); // 00 + index - sort by row index
				gameProfile = new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name);

				Object entityPlayer = ReflectionUtils.getHandle(player);

				GameProfile currentProfile = (GameProfile) ReflectionUtils.invokeMethod(entityPlayer, "getProfile",
						true, true);

				gameProfile.getProperties().clear();
				gameProfile.getProperties().putAll(currentProfile.getProperties());

				Field profile = null;
				for (Field gp : entityPlayer.getClass().getSuperclass().getDeclaredFields()) {
					if (gp.getType().equals(currentProfile.getClass())) {
						ReflectionUtils.modifyFinalField(profile = gp, entityPlayer, gameProfile);
						break;
					}
				}

				if (profile == null) {
					return;
				}

				Object entityPlayerArray = Array.newInstance(entityPlayer.getClass(), 1);
				Array.set(entityPlayerArray, 0, entityPlayer);

				packet = packetPlayOutPlayerInfo.getConstructor(enumPlayerInfoAction, entityPlayerArray.getClass())
						.newInstance(enumPlayerInfoAction.getDeclaredField("ADD_PLAYER").get(enumPlayerInfoAction),
								entityPlayerArray);

				((List<Object>) infoList.get(packet)).add(rowPlayer = playerInfoDataConstr.newInstance(packet,
						gameProfile, ping, gameMode, ReflectionUtils.getAsIChatBaseComponent(player.getName())));

				for (TabListUser user : plugin.getUsers()) {
					ReflectionUtils.sendPacket(user.getPlayer(), packet);
					ReflectionUtils.sendPacket(user.getPlayer(), rowPlayer);
				}

				ReflectionUtils.modifyFinalField(profile, entityPlayer, currentProfile);

				for (TabListUser user : plugin.getUsers()) {
					ReflectionUtils.sendPacket(user.getPlayer(), packet);
					ReflectionUtils.sendPacket(user.getPlayer(), rowPlayer);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, 3L);
	}

	@Override
	public UUID getHeadId() {
		return skinId;
	}

	@Override
	public void create(int rowIndex) {
		if (rowPlayer != null || player != null || playOutPlayerInfoConstr == null) {
			return;
		}

		if (gameProfile == null) {
			String name = String.format("%03d", this.rowIndex = rowIndex); // 00 + index - sort by row index
			gameProfile = new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name);
		}

		CompletableFuture<Void> comp = new CompletableFuture<>();
		if (ServerVersion.isCurrentEqualOrHigher(ServerVersion.v1_8_R2) && Bukkit.getServer().getOnlineMode()
				&& skinId != null) {
			comp = ReflectionUtils.getJsonComponent().getSkinValue(skinId.toString()).thenAcceptAsync(map -> {
				java.util.Map.Entry<String, String> first = map.pollFirstEntry();
				gameProfile.getProperties().get("textures").clear();
				gameProfile.getProperties().put("textures", new Property("textures", first.getKey(), first.getValue()));
			});
		} else {
			comp.complete(null);
		}

		comp.thenAccept(v -> {
			try {
				packet = playOutPlayerInfoConstr.newInstance(
						enumPlayerInfoAction.getDeclaredField("ADD_PLAYER").get(enumPlayerInfoAction),
						Array.newInstance(entityPlayer, 0));

				((List<Object>) infoList.get(packet)).add(rowPlayer = playerInfoDataConstr.newInstance(packet,
						gameProfile, ping, gameMode, ReflectionUtils.getAsIChatBaseComponent(text)));

				for (TabListUser user : plugin.getUsers()) {
					ReflectionUtils.sendPacket(user.getPlayer(), packet);
					ReflectionUtils.sendPacket(user.getPlayer(), rowPlayer);
				}

				// Send a new packet for empty rows
				packet = playOutPlayerInfoConstr.newInstance(
						enumPlayerInfoAction.getDeclaredField("UPDATE_DISPLAY_NAME").get(enumPlayerInfoAction),
						Array.newInstance(entityPlayer, 0));

				((List<Object>) infoList.get(packet)).add(rowPlayer = playerInfoDataConstr.newInstance(packet,
						gameProfile, ping, gameMode, ReflectionUtils.getAsIChatBaseComponent(text)));

				for (TabListUser user : plugin.getUsers()) {
					ReflectionUtils.sendPacket(user.getPlayer(), packet);
					ReflectionUtils.sendPacket(user.getPlayer(), rowPlayer);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	public void show(Player player) {
		if (packet != null && rowPlayer != null) {
			ReflectionUtils.sendPacket(player, packet);
			ReflectionUtils.sendPacket(player, rowPlayer);
		}
	}

	@Override
	public void remove() {
		if (rowPlayer == null) {
			return;
		}

		try {
			packet = playOutPlayerInfoConstr.newInstance(
					enumPlayerInfoAction.getDeclaredField("REMOVE_PLAYER").get(enumPlayerInfoAction),
					Array.newInstance(entityPlayer, 0));

			((List<Object>) infoList.get(packet)).add(rowPlayer = playerInfoDataConstr.newInstance(packet, gameProfile,
					ping, gameMode, ReflectionUtils.getAsIChatBaseComponent(text)));

			for (TabListUser user : plugin.getUsers()) {
				ReflectionUtils.sendPacket(user.getPlayer(), packet);
				ReflectionUtils.sendPacket(user.getPlayer(), rowPlayer);
			}

			// Restore player to default state
			if (player != null) {
				packet = playOutPlayerInfoConstr.newInstance(
						enumPlayerInfoAction.getDeclaredField("ADD_PLAYER").get(enumPlayerInfoAction),
						Array.newInstance(entityPlayer, 0));

				((List<Object>) infoList.get(packet)).add(rowPlayer = playerInfoDataConstr.newInstance(packet,
						gameProfile, ping, gameMode,
						ReflectionUtils.getAsIChatBaseComponent(plugin.getComplement().getPlayerListName(player))));

				for (TabListUser user : plugin.getUsers()) {
					ReflectionUtils.sendPacket(user.getPlayer(), packet);
					ReflectionUtils.sendPacket(user.getPlayer(), rowPlayer);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// Mark as null and garbage to make sure it is removed
			rowPlayer = null;
		}
	}

	@Override
	public String updateText(Player player, String text) {
		if (rowPlayer == null || player == null || this.player != null || text.trim().isEmpty()) {
			// Player names should only be changed in groups
			//
			// Do not update empty entries too frequently
			return text;
		}

		text = plugin.makeAnim(text);

		if (replacer.isRequestUpdate()) {
			text = (this.text = replacer.replaceVariables(text));
		}

		text = plugin.getPlaceholders().replaceVariables(player, text);

		if (ServerVersion.isCurrentLower(ServerVersion.v1_16_R1)) {
			text = Util.colorMsg(text);
		}

		try {
			// ReflectionUtils.modifyFinalField(ReflectionUtils.getField(rowPlayer, "e"),
			// rowPlayer, ReflectionUtils.getAsIChatBaseComponent(text));

			// TODO Improve?
			packet = playOutPlayerInfoConstr.newInstance(
					enumPlayerInfoAction.getDeclaredField("UPDATE_DISPLAY_NAME").get(enumPlayerInfoAction),
					Array.newInstance(entityPlayer, 0));

			((List<Object>) infoList.get(packet)).add(rowPlayer = playerInfoDataConstr.newInstance(packet, gameProfile,
					ping, gameMode, ReflectionUtils.getAsIChatBaseComponent(text)));

			ReflectionUtils.sendPacket(player, packet);
			ReflectionUtils.sendPacket(player, rowPlayer);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return text;
	}

	@Override
	public void setSkin(UUID skinId) {
		if (player != null || skinId == null || skinId.equals(this.skinId)) {
			return;
		}

		this.skinId = skinId;

		if (!Bukkit.getServer().getOnlineMode() || ServerVersion.isCurrentLower(ServerVersion.v1_8_R2)) {
			return;
		}

		ReflectionUtils.getJsonComponent().getSkinValue(skinId.toString()).thenAcceptAsync(map -> {
			java.util.Map.Entry<String, String> first = map.pollFirstEntry();
			gameProfile.getProperties().get("textures").clear();
			gameProfile.getProperties().put("textures", new Property("textures", first.getKey(), first.getValue()));

			try {
				packet = playOutPlayerInfoConstr.newInstance(
						enumPlayerInfoAction.getDeclaredField("UPDATE_DISPLAY_NAME").get(enumPlayerInfoAction),
						Array.newInstance(ReflectionUtils.getNMSClass("EntityPlayer"), 0));

				((List<Object>) infoList.get(packet)).add(rowPlayer = playerInfoDataConstr.newInstance(packet,
						gameProfile, ping, gameMode, ReflectionUtils.getAsIChatBaseComponent(text)));

				for (TabListUser user : plugin.getUsers()) {
					ReflectionUtils.sendPacket(user.getPlayer(), packet);
					ReflectionUtils.sendPacket(user.getPlayer(), rowPlayer);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
}