/*
 * Copyright 2019-2021 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.cloudnetservice.modules.npc.platform.bukkit.command;

import com.github.juliarn.npc.profile.Profile;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.driver.service.GroupConfiguration;
import eu.cloudnetservice.cloudnet.bukkitcommands.BaseTabExecutor;
import eu.cloudnetservice.modules.npc.NPC;
import eu.cloudnetservice.modules.npc.NPC.ClickAction;
import eu.cloudnetservice.modules.npc.NPC.NPCType;
import eu.cloudnetservice.modules.npc.NPC.ProfileProperty;
import eu.cloudnetservice.modules.npc.configuration.NPCConfigurationEntry;
import eu.cloudnetservice.modules.npc.platform.PlatformSelectorEntity;
import eu.cloudnetservice.modules.npc.platform.bukkit.BukkitPlatformNPCManagement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NPCCommand extends BaseTabExecutor {

  private static final List<String> TRUE_FALSE = Arrays.asList("true", "yes", "y", "false", "no", "n");

  private static final List<String> NPC_TYPES = Arrays.stream(NPCType.values())
    .map(Enum::name)
    .collect(Collectors.toList());
  private static final List<String> CLICK_ACTIONS = Arrays.stream(ClickAction.values())
    .map(Enum::name)
    .collect(Collectors.toList());
  private static final Map<String, Integer> VALID_ITEM_SLOTS = ImmutableMap.<String, Integer>builder()
    .put("MAIN_HAND", 0)
    .put("OFF_HAND", 1)
    .put("BOOTS", 2)
    .put("LEGGINS", 3)
    .put("CHESTPLATE", 4)
    .put("HELMET", 5)
    .build();

  private static final String COPIED_NPC_KEY = "npc_copy_entry";

  private final Plugin plugin;
  private final BukkitPlatformNPCManagement management;

  public NPCCommand(@NotNull Plugin plugin, @NotNull BukkitPlatformNPCManagement management) {
    this.plugin = plugin;
    this.management = management;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    // validate that the sender is a player
    if (!(sender instanceof Player)) {
      sender.sendMessage("§cOnly players can execute the /cn command!");
      return true;
    }

    var player = (Player) sender;

    // get the npc configuration entry for the current group
    var entry = this.management.getApplicableNPCConfigurationEntry();
    if (entry == null) {
      sender.sendMessage("§cThere is no applicable npc configuration entry for this service (yet)!");
      return true;
    }

    // npc create
    if (args.length >= 5 && args[0].equalsIgnoreCase("create")) {
      // 0: target group
      var targetGroup = args[1];
      // 1: mob type
      var npcType = Enums.getIfPresent(NPCType.class, args[2].toUpperCase()).orNull();
      if (npcType == null) {
        sender.sendMessage("§cNo such NPC type, use one of: " + String.join(", ", NPC_TYPES));
        return true;
      }
      // 3...: display name parts
      var displayName = String.join(" ", Arrays.copyOfRange(args, 4, args.length)).trim();
      if (displayName.length() > 16) {
        sender.sendMessage("§cThe display name can only contain up to 16 chars.");
        return true;
      }
      // 2: skin owner or entity type, depends on 1
      if (npcType == NPCType.PLAYER) {
        // load the profile
        var profile = new Profile(args[3]);
        if (!profile.complete()) {
          sender.sendMessage(String.format("§cUnable to complete profile of §6%s§c!", args[3]));
          return true;
        }
        // create the npc
        var npc = NPC.builder()
          .profileProperties(profile.getProperties().stream()
            .map(property -> new ProfileProperty(property.getName(), property.getValue(), property.getSignature()))
            .collect(Collectors.toSet()))
          .displayName(displayName)
          .targetGroup(targetGroup)
          .location(this.management.toWorldPosition(player.getLocation(), entry.getTargetGroup()))
          .build();
        this.management.createNPC(npc);
      } else {
        // get the entity type
        var entityType = Enums.getIfPresent(EntityType.class, args[3].toUpperCase()).orNull();
        if (entityType == null
          || !entityType.isSpawnable()
          || !entityType.isAlive()
          || entityType == EntityType.PLAYER
        ) {
          sender.sendMessage(String.format("§cYou can not spawn a selector mob of type §6%s§c!", args[3]));
          return true;
        }
        // create the npc
        var npc = NPC.builder()
          .entityType(entityType.name())
          .displayName(displayName)
          .targetGroup(targetGroup)
          .location(this.management.toWorldPosition(player.getLocation(), entry.getTargetGroup()))
          .build();
        this.management.createNPC(npc);
      }

      // done :)
      sender.sendMessage("§7The service selector mob was created §asuccessfully§7!");
      return true;
    }

    // npc operations
    if (args.length == 1) {
      switch (args[0].toLowerCase()) {
        // remove the nearest npc
        case "rm":
        case "remove": {
          var npc = this.getNearestNPC(player.getLocation());
          if (npc == null) {
            sender.sendMessage("§cNo npc in range found! Make sure the npc you want to edit is in a 5 block radius.");
            return true;
          }
          // remove the npc
          this.management.deleteNPC(npc);
          sender.sendMessage("§cThe npc was removed successfully! This may take a few seconds to show effect!");
          return true;
        }
        // removes all npcs in unloaded worlds
        case "cu":
        case "cleanup": {
          this.management.getTrackedEntities().values().stream()
            .map(PlatformSelectorEntity::getNPC)
            .filter(npc -> Bukkit.getWorld(npc.getLocation().getWorld()) == null)
            .forEach(npc -> {
              this.management.deleteNPC(npc);
              sender.sendMessage(String.format(
                "§cAn entity in the world §6%s §cwas removed! This may take a few seconds to show effect!",
                npc.getLocation().getWorld()));
            });
          return true;
        }
        // copies the current npc
        case "cp":
        case "copy": {
          var npc = this.getNearestNPC(player.getLocation());
          if (npc == null) {
            sender.sendMessage("§cNo npc in range found! Make sure the npc you want to edit is in a 5 block radius.");
            return true;
          }
          // check if the player has already a npc in the clipboard
          if (player.getMetadata(COPIED_NPC_KEY).isEmpty()) {
            // add the metadata
            player.setMetadata(COPIED_NPC_KEY, new FixedMetadataValue(this.plugin, NPC.builder(npc)));
            sender.sendMessage("§7The npc was copied §asuccessfully §7to your clipboard");
          } else {
            sender.sendMessage("§cThere is a npc already in your clipboard! Paste it or clear your clipboard.");
          }
          return true;
        }
        // clears the clipboard
        case "ccb":
        case "clearclipboard": {
          player.removeMetadata(COPIED_NPC_KEY, this.plugin);
          sender.sendMessage("§7Your clipboard was cleared §asuccessfully§7.");
          return true;
        }
        // cuts the npc
        case "cut": {
          var npc = this.getNearestNPC(player.getLocation());
          if (npc == null) {
            sender.sendMessage("§cNo npc in range found! Make sure the npc you want to edit is in a 5 block radius.");
            return true;
          }
          // check if the player has already a npc in the clipboard
          if (player.getMetadata(COPIED_NPC_KEY).isEmpty()) {
            // remove the npc
            this.management.deleteNPC(npc);
            // add the metadata
            player.setMetadata(COPIED_NPC_KEY, new FixedMetadataValue(this.plugin, NPC.builder(npc)));
            sender.sendMessage("§7The npc was cut §asuccessfully §7to your clipboard");
          } else {
            sender.sendMessage("§cThere is a npc already in your clipboard! Paste it or clear your clipboard.");
          }
          return true;
        }
        // pastes the npc
        case "paste": {
          var values = player.getMetadata(COPIED_NPC_KEY);
          if (values.isEmpty()) {
            sender.sendMessage("§cThere is no npc in your clipboard!");
            return true;
          }
          // paste the npc
          var npc = ((NPC.Builder) values.get(0).value())
            .location(this.management.toWorldPosition(player.getLocation(), entry.getTargetGroup()))
            .build();
          this.management.createNPC(npc);
          sender.sendMessage("§7The service selector mob was pasted §asuccessfully§7!");
          // clear the clipboard
          player.removeMetadata(COPIED_NPC_KEY, this.plugin);
          return true;
        }
        // lists all npcs
        case "list": {
          sender.sendMessage(String.format("§7There are §6%s §7selector mobs:", this.management.getNPCs().size()));
          for (var npc : this.management.getNPCs()) {
            sender.sendMessage(String.format(
              "§8> §6\"%s\" §8@ §7%s§8/§7%s §8- §7%d, %d, %d in \"%s\"",
              npc.getDisplayName(),
              npc.getNpcType(),
              npc.getNpcType() == NPCType.ENTITY ? npc.getEntityType() : "props: " + npc.getProfileProperties().size(),
              (int) npc.getLocation().getX(),
              (int) npc.getLocation().getY(),
              (int) npc.getLocation().getZ(),
              npc.getLocation().getWorld()));
          }
          return true;
        }
        default: {
          sender.sendMessage(String.format("§cUnknown sub-command option: §6%s§c.", args[0]));
          return true;
        }
      }
    }

    // npc edit
    if (args.length >= 3 && args[0].equalsIgnoreCase("edit")) {
      final var npc = this.getNearestNPC(player.getLocation());
      if (npc == null) {
        sender.sendMessage("§cNo npc in range found! Make sure the npc you want to edit is in a 5 block radius.");
        return true;
      }
      NPC updatedNpc;
      // find the option the player is trying to edit
      switch (args[1].toLowerCase()) {
        // edit of the display name
        case "display": {
          var displayName = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
          if (displayName.length() > 16) {
            sender.sendMessage("§cThe display name can only contain up to 16 chars.");
            return true;
          }
          // re-create the npc with the given options
          updatedNpc = NPC.builder(npc)
            .displayName(ChatColor.translateAlternateColorCodes('&', displayName))
            .build();
          break;
        }
        // enable that the npc looks at the player
        case "lap":
        case "lookatplayer": {
          if (this.canChangeSetting(sender, npc)) {
            updatedNpc = NPC.builder(npc).lookAtPlayer(this.parseBoolean(args[2])).build();
            break;
          } else {
            return true;
          }
        }
        // if the npc should imitate the player
        case "ip":
        case "imitateplayer": {
          if (this.canChangeSetting(sender, npc)) {
            updatedNpc = NPC.builder(npc).imitatePlayer(this.parseBoolean(args[2])).build();
            break;
          } else {
            return true;
          }
        }
        // if the npc should use the skin of the player being spawned to
        case "ups":
        case "useplayerskin": {
          if (this.canChangeSetting(sender, npc)) {
            updatedNpc = NPC.builder(npc).usePlayerSkin(this.parseBoolean(args[2])).build();
            break;
          } else {
            return true;
          }
        }
        // sets the glowing color
        case "gc":
        case "glowingcolor": {
          // try to parse the color
          var chatColor = ChatColor.getByChar(args[2]);
          if (chatColor == null) {
            sender.sendMessage(String.format(
              "§cNo such chat color char §6%s§c! Use one of §8[§60-9§8, §6a-f§8, §6r§8]§c.",
              args[2]));
            return true;
          }
          // validate the color
          if (chatColor.isFormat()) {
            sender.sendMessage("§cPlease use a color char, not a chat formatting char!");
            return true;
          }
          // disable glowing if the color is reset
          if (chatColor == ChatColor.RESET) {
            updatedNpc = NPC.builder(npc).glowing(false).build();
          } else {
            updatedNpc = NPC.builder(npc).glowing(true).glowingColor(String.valueOf(chatColor.getChar())).build();
          }
          break;
        }
        // sets if the npc should "fly" with an elytra
        case "fwe":
        case "flyingwithelytra": {
          if (this.canChangeSetting(sender, npc)) {
            var enabled = this.parseBoolean(args[2]);
            updatedNpc = NPC.builder(npc).flyingWithElytra(enabled).build();
            // warn about weird behaviour in combination with other settings
            if (enabled) {
              sender.sendMessage("§cEnabling elytra-flying might lead to weird-looking behaviour when imitate "
                + "and lookAt player is enabled! Consider disabling these options.");
            }
            break;
          } else {
            return true;
          }
        }
        // the floating item of the npc
        case "fi":
        case "floatingitem": {
          // convert null to "no item"
          if (args[2].equalsIgnoreCase("null")) {
            updatedNpc = NPC.builder(npc).floatingItem(null).build();
            break;
          }
          // get the material of the item
          var material = Material.matchMaterial(args[2]);
          if (material == null) {
            sender.sendMessage(String.format("§cNo material found by query: §6%s§c.", args[2]));
            return true;
          } else {
            updatedNpc = NPC.builder(npc).floatingItem(material.name()).build();
            break;
          }
        }
        // the left click action
        case "lca":
        case "leftclickaction": {
          var action = Enums.getIfPresent(ClickAction.class, args[2].toUpperCase()).orNull();
          if (action == null) {
            sender.sendMessage(String.format(
              "§cNo such click action. Use one of: §6%s§c.",
              String.join(", ", CLICK_ACTIONS)));
            return true;
          } else {
            updatedNpc = NPC.builder(npc).leftClickAction(action).build();
            break;
          }
        }
        // the right click action
        case "rca":
        case "rightclickaction": {
          var action = Enums.getIfPresent(ClickAction.class, args[2].toUpperCase()).orNull();
          if (action == null) {
            sender.sendMessage(String.format(
              "§cNo such click action. Use one of: §6%s§c.",
              String.join(", ", CLICK_ACTIONS)));
            return true;
          } else {
            updatedNpc = NPC.builder(npc).rightClickAction(action).build();
            break;
          }
        }
        // sets the items
        case "items": {
          if (args.length != 4) {
            sender.sendMessage("§cInvalid usage! Use §6/cn edit items <slot> <material>§c!");
            return true;
          }
          // parse the slot
          var slot = VALID_ITEM_SLOTS.get(args[2].toUpperCase());
          if (slot == null) {
            sender.sendMessage(String.format(
              "§cNo such item slot! Use one of §6%s§7.",
              String.join(", ", VALID_ITEM_SLOTS.keySet())));
            return true;
          }
          // parse the item
          var item = Material.matchMaterial(args[3]);
          if (item == null) {
            sender.sendMessage("§cNo such material!");
            return true;
          }
          // a little hack here :)
          npc.getItems().put(slot, item.name());
          updatedNpc = npc;
          break;
        }
        // edit the info lines
        case "il":
        case "infolines": {
          if (args.length < 4) {
            sender.sendMessage("§cInvalid usage! Use §6/cn edit il <index> <new line content>§c!");
            return true;
          }
          // parse the index
          var index = Ints.tryParse(args[2]);
          if (index == null) {
            sender.sendMessage(String.format("§cUnable to parse index from string §6%s§c.", args[2]));
            return true;
          }
          // get the new line content
          var content = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
          if (content.equals("null")) {
            // remove the info line if there
            if (npc.getInfoLines().size() > index) {
              npc.getInfoLines().remove((int) index);
              updatedNpc = npc;
              break;
            } else {
              sender.sendMessage(String.format("§cNo info line at index §6%d§c.", index));
              return true;
            }
          } else {
            content = ChatColor.translateAlternateColorCodes('&', content);
            // set the info line add the location or add it
            if (npc.getInfoLines().size() > index) {
              npc.getInfoLines().set(index, content);
            } else {
              npc.getInfoLines().add(content);
            }
            updatedNpc = npc;
            break;
          }
        }
        // change the profile (will force-set the entity type to npc)
        case "profile": {
          var profile = new Profile(args[2]);
          if (!profile.complete()) {
            sender.sendMessage(String.format("§cUnable to complete profile of §6%s§c!", args[2]));
            return true;
          } else {
            updatedNpc = NPC.builder(npc).profileProperties(profile.getProperties().stream()
                .map(property -> new ProfileProperty(property.getName(), property.getValue(), property.getSignature()))
                .collect(Collectors.toSet()))
              .build();
            break;
          }
        }
        // change the entity type (will force-set the entity type to entity)
        case "et":
        case "entitytype": {
          var entityType = Enums.getIfPresent(EntityType.class, args[2].toUpperCase()).orNull();
          if (entityType == null) {
            sender.sendMessage(String.format("§cNo such entity type: §6%s§c.", args[2].toUpperCase()));
            return true;
          } else {
            updatedNpc = NPC.builder(npc).entityType(entityType.name()).build();
            break;
          }
        }
        // sets the target group of the npc
        case "tg":
        case "targetgroup": {
          updatedNpc = NPC.builder(npc).targetGroup(args[2]).build();
          break;
        }
        // unknown option
        default:
          sender.sendMessage(String.format("§cNo option with name §6%s §cfound!", args[1].toLowerCase()));
          return true;
      }
      // update & notify
      this.management.createNPC(updatedNpc);
      sender.sendMessage(String.format(
        "§7The option §6%s was updated §asuccessfully§7! It may take a few seconds for the change to become visible.",
        args[1].toLowerCase()));
      return true;
    }

    sender.sendMessage("§8> §7/cn create <targetGroup> <type> <skinOwnerName/entityType> <displayName>");
    sender.sendMessage("§8> §7/cn edit <option> <value...>");
    sender.sendMessage("§8> §7/cn remove");
    sender.sendMessage("§8> §7/cn cleanup");
    sender.sendMessage("§8> §7/cn list");
    sender.sendMessage("§8> §7/cn <copy/cut/paste>");
    return true;
  }

  @Override
  public @NotNull Collection<String> tabComplete(@NotNull CommandSender sender, String @NotNull [] args) {
    // top level commands
    if (args.length == 1) {
      return Arrays.asList("create", "edit", "remove", "cleanup", "list", "copy", "cut", "paste", "clearclipboard");
    }
    // create arguments
    if (args[0].equalsIgnoreCase("create")) {
      switch (args.length) {
        case 2:
          return CloudNetDriver.getInstance().getGroupConfigurationProvider().getGroupConfigurations().stream()
            .map(GroupConfiguration::getName)
            .collect(Collectors.toList());
        case 3:
          return NPC_TYPES;
        case 4: {
          // try to give a suggestion based on the previous input
          var type = Enums.getIfPresent(NPCType.class, args[2].toUpperCase()).orNull();
          if (type != null) {
            if (type == NPCType.ENTITY) {
              return Arrays.stream(EntityType.values())
                .filter(EntityType::isAlive)
                .filter(EntityType::isSpawnable)
                .filter(entityType -> entityType != EntityType.PLAYER)
                .map(Enum::name)
                .collect(Collectors.toList());
            } else {
              return Arrays.asList("derklaro", "juliarn", "0utplayyyy");
            }
          }
          return Collections.emptyList();
        }
        default:
          return Collections.emptyList();
      }
    }
    // edit commands
    if (args[0].equalsIgnoreCase("edit")) {
      // top level options
      if (args.length == 2) {
        return Arrays.asList(
          "display",
          "lookatplayer",
          "imitateplayer",
          "useplayerskin",
          "glowingcolor",
          "flyingwithelytra",
          "floatingitem",
          "leftclickaction",
          "rightclickaction",
          "items",
          "infolines",
          "profile",
          "entitytype",
          "targetgroup");
      }
      // value options
      if (args.length == 3) {
        switch (args[1].toLowerCase()) {
          // true-false options
          case "lap":
          case "lookatplayer":
          case "ip":
          case "imitateplayer":
          case "ups":
          case "useplayerskin":
          case "fwe":
          case "flyingwithelytra":
            return TRUE_FALSE;
          // click action options
          case "lca":
          case "leftclickaction":
          case "rca":
          case "rightclickaction":
            return CLICK_ACTIONS;
          // color options
          case "gc":
          case "glowingcolor":
            return Arrays.stream(ChatColor.values())
              .filter(ChatColor::isColor)
              .map(color -> String.valueOf(color.getChar()))
              .collect(Collectors.toList());
          // entity type
          case "et":
          case "entitytype":
            return Arrays.stream(EntityType.values())
              .filter(EntityType::isAlive)
              .filter(EntityType::isSpawnable)
              .filter(type -> type != EntityType.PLAYER)
              .map(Enum::name)
              .collect(Collectors.toList());
          // npc skin profile
          case "profile":
            return Arrays.asList("derklaro", "juliarn", "0utplayyyy");
          // target group
          case "tg":
          case "targetgroup":
            return CloudNetDriver.getInstance().getGroupConfigurationProvider().getGroupConfigurations().stream()
              .map(GroupConfiguration::getName)
              .collect(Collectors.toList());
          // item slots
          case "items":
            return new ArrayList<>(VALID_ITEM_SLOTS.keySet());
          // info lines top level
          case "il":
          case "infolines":
            return Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
          // floating item
          case "fi":
          case "floatingitem":
            return Arrays.stream(Material.values()).map(Material::name).collect(Collectors.toList());
          // unknown or non-completable option
          default:
            return Collections.emptyList();
        }
      }
      // more...
      if (args.length == 4 && args[1].equalsIgnoreCase("items")) {
        return Arrays.stream(Material.values()).map(Material::name).collect(Collectors.toList());
      }
    }
    // unable to tab-complete
    return Collections.emptyList();
  }

  private @Nullable NPC getNearestNPC(@NotNull Location location) {
    return this.management.getTrackedEntities().values().stream()
      .filter(PlatformSelectorEntity::isSpawned)
      .filter(entity -> entity.getLocation().getWorld().getUID().equals(location.getWorld().getUID()))
      .filter(entity -> entity.getLocation().distanceSquared(location) <= 10)
      .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(location)))
      .map(PlatformSelectorEntity::getNPC)
      .orElse(null);
  }

  private boolean parseBoolean(@NotNull String input) {
    return input.contains("true") || input.contains("yes") || input.startsWith("y");
  }

  private boolean canChangeSetting(@NotNull CommandSender sender, @NotNull NPC npc) {
    if (npc.getNpcType() != NPCType.PLAYER) {
      sender.sendMessage(String.format("§cThis option is not available for the npc type §6%s§c!", npc.getEntityType()));
      return false;
    }
    return true;
  }
}