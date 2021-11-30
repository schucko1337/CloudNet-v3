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

package eu.cloudnetservice.modules.smart.listener;

import de.dytanic.cloudnet.driver.event.EventListener;
import de.dytanic.cloudnet.event.task.LocalServiceTaskAddEvent;
import eu.cloudnetservice.modules.smart.SmartServiceTaskConfig;
import org.jetbrains.annotations.NotNull;

public final class CloudNetLocalServiceTaskListener {

  @EventListener
  public void handle(@NotNull LocalServiceTaskAddEvent event) {
    if (!event.getTask().getProperties().contains("smartConfig")) {
      event.getTask().getProperties().append("smartConfig", SmartServiceTaskConfig.builder().build());
    }
  }
}