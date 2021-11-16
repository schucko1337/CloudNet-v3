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

package de.dytanic.cloudnet.ext.cloudperms.nukkit;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.plugin.PluginBase;
import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.ext.cloudperms.PermissionsUpdateListener;
import de.dytanic.cloudnet.ext.cloudperms.nukkit.listener.NukkitCloudPermissionsPlayerListener;
import de.dytanic.cloudnet.wrapper.Wrapper;

public final class NukkitCloudPermissionsPlugin extends PluginBase {

  @Override
  public void onEnable() {
    this.injectPlayersCloudPermissible();

    super.getServer().getPluginManager().registerEvents(
      new NukkitCloudPermissionsPlayerListener(CloudNetDriver.getInstance().getPermissionManagement()),
      this);
    CloudNetDriver.getInstance().getEventManager().registerListener(new PermissionsUpdateListener<>(
      runnable -> Server.getInstance().getScheduler().scheduleTask(this, runnable),
      Player::sendCommandData,
      Player::getUniqueId,
      uuid -> Server.getInstance().getPlayer(uuid).orElse(null),
      () -> Server.getInstance().getOnlinePlayers().values()));
  }

  @Override
  public void onDisable() {
    CloudNetDriver.getInstance().getEventManager().unregisterListeners(this.getClass().getClassLoader());
    Wrapper.getInstance().unregisterPacketListenersByClassLoader(this.getClass().getClassLoader());
  }

  private void injectPlayersCloudPermissible() {
    for (Player player : Server.getInstance().getOnlinePlayers().values()) {
      NukkitPermissionInjectionHelper.injectPermissible(player, CloudNetDriver.getInstance().getPermissionManagement());
    }
  }
}