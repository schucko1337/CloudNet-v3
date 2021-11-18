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

package de.dytanic.cloudnet.command;


import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.parsers.Parser;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import de.dytanic.cloudnet.CloudNet;
import de.dytanic.cloudnet.command.annotation.CommandAlias;
import de.dytanic.cloudnet.command.annotation.Description;
import de.dytanic.cloudnet.command.defaults.DefaultCommandProvider;
import de.dytanic.cloudnet.command.source.CommandSource;
import de.dytanic.cloudnet.command.source.DriverCommandSource;
import de.dytanic.cloudnet.common.language.I18n;
import de.dytanic.cloudnet.console.IConsole;
import de.dytanic.cloudnet.driver.NodeTestUtility;
import de.dytanic.cloudnet.driver.command.CommandInfo;
import de.dytanic.cloudnet.driver.event.DefaultEventManager;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.collections.Iterables;

public final class CommandProviderTest {

  private static final CommandProvider commandProvider = new DefaultCommandProvider(Mockito.mock(IConsole.class));

  @BeforeAll
  public static void initNode() {
    CloudNet node = NodeTestUtility.mockAndSetDriverInstance();
    Mockito.when(node.getCommandProvider()).thenReturn(commandProvider);
    Mockito.when(node.getEventManager()).thenReturn(new DefaultEventManager());
    commandProvider.register(new CommandTest());
    commandProvider.register(new CommandHelpTest());
  }

  @Test
  public void testCommandRegistration() {
    CommandInfo testCommand = commandProvider.getCommand("tests");
    Assertions.assertNotNull(testCommand);
    Assertions.assertEquals(1, testCommand.getUsage().size());
    Assertions.assertEquals("tests test <user>", Iterables.firstOf(testCommand.getUsage()));

    CommandInfo testCommandByAlias = commandProvider.getCommand("test1");
    Assertions.assertNotEquals("test1", testCommand.getName());
    Assertions.assertEquals(testCommandByAlias, testCommandByAlias);
  }

  @Test
  public void testStaticCommandSuggestions() {
    DriverCommandSource source = new DriverCommandSource();

    List<String> rootSuggestions = commandProvider.suggest(source, "tests");
    Assertions.assertEquals(2, rootSuggestions.size());
    Assertions.assertEquals(Arrays.asList("help", "tests"), rootSuggestions);

    List<String> subSuggestions = commandProvider.suggest(source, "tests ");
    Assertions.assertEquals(1, subSuggestions.size());
    Assertions.assertEquals("test", Iterables.firstOf(subSuggestions));
  }

  @Test
  public void testDynamicCommandSuggestions() {
    DriverCommandSource source = new DriverCommandSource();

    List<String> suggestions = commandProvider.suggest(source, "tests test ");
    Assertions.assertEquals(3, suggestions.size());
    Assertions.assertEquals(Arrays.asList("alice", "bob", "clyde"), suggestions);
  }

  @Test
  public void testCommandNotFound() {
    DriverCommandSource source = new DriverCommandSource();

    commandProvider.execute(source, "non existing command");
    Assertions.assertEquals(1, source.getMessages().size());
    Assertions.assertEquals(I18n.trans("command-not-found"), Iterables.firstOf(source.getMessages()));
  }

  @Test
  public void testCommandUnregister() {
    Assertions.assertEquals(2, commandProvider.getCommands().size());
    commandProvider.unregister(this.getClass().getClassLoader());
    Assertions.assertEquals(0, commandProvider.getCommands().size());
  }

  public static final class CommandHelpTest {

    @CommandMethod("help")
    public void testHelpCommand(CommandSource source) {
      // no response
    }
  }

  @CommandAlias({"test1", "test2"})
  @Description("CommandTest description")
  @CommandPermission("command.test.permission")
  public static final class CommandTest {

    @Parser(name = "UserParser", suggestions = "UserSuggestions")
    public String parseUser(CommandContext<CommandSource> $, Queue<String> input) {
      return input.remove();
    }

    @Suggestions("UserSuggestions")
    public List<String> suggestUsers(CommandContext<CommandSource> $, String input) {
      return Arrays.asList("alice", "bob", "clyde");
    }

    @CommandMethod("tests test <user>")
    public void testUserCommand(CommandSource source,
      @Argument(value = "user", parserName = "UserParser") String user) {
      source.sendMessage(user);
    }
  }
}