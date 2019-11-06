package uk.co.angrybee.joe;

import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

// handles Discord interaction
public class ServerDiscordClient extends ListenerAdapter
{
    public static String[] allowedToAddRemoveRoles;
    public static String[] allowedToAddRoles;
    public static String[] allowedToAddLimitedRoles;

    final char[] validCharacters = {'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p', 'a', 's', 'd', 'f', 'g', 'h',
    'j', 'k', 'l', 'z', 'x', 'c', 'v', 'b', 'n', 'm', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '_'};

    public int InitializeClient(String clientToken)
    {
        try
        {
            JDA javaDiscordAPI = new JDABuilder(AccountType.BOT)
                    .setToken(clientToken)
                    .addEventListeners(new ServerDiscordClient())
                    .build();
            javaDiscordAPI.awaitReady();
            return 0;
        }
        catch(LoginException | InterruptedException e)
        {
            e.printStackTrace();
            return 1;
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent messageReceivedEvent)
    {
        if(messageReceivedEvent.isFromType(ChannelType.TEXT))
        {
            boolean correctChannel = false;

            for(int channel = 0; channel < DiscordWhitelister.getWhitelisterBotConfig().getList("target-text-channels").size(); ++channel)
            {
                if(messageReceivedEvent.getTextChannel().getId().equals(DiscordWhitelister.getWhitelisterBotConfig().getList("target-text-channels").get(channel)))
                {
                    correctChannel = true;
                }
            }

            if(correctChannel && !messageReceivedEvent.getAuthor().isBot())
            {
                // message context
                User author = messageReceivedEvent.getAuthor();
                String messageContents = messageReceivedEvent.getMessage().getContentDisplay();
                TextChannel channel = messageReceivedEvent.getTextChannel();

                boolean userCanAddRemove = false;
                boolean userCanAdd = false;
                boolean userHasLimitedAdd = false;

                for(Role role : messageReceivedEvent.getGuild().getMember(messageReceivedEvent.getAuthor()).getRoles())
                {
                    if(Arrays.stream(allowedToAddRemoveRoles).parallel().anyMatch(role.getName()::equalsIgnoreCase))
                    {
                        userCanAddRemove = true;
                    }
                }

                for(Role role : messageReceivedEvent.getGuild().getMember(messageReceivedEvent.getAuthor()).getRoles())
                {
                    if(Arrays.stream(allowedToAddRoles).parallel().anyMatch(role.getName()::equalsIgnoreCase))
                    {
                        userCanAdd = true;
                    }
                }

                // limited add check
                if(DiscordWhitelister.getWhitelisterBotConfig().getBoolean("limited-whitelist-enabled"))
                {
                    // check if user is in a limited add role
                    for(Role role : messageReceivedEvent.getGuild().getMember(messageReceivedEvent.getAuthor()).getRoles())
                    {
                        if(Arrays.stream(allowedToAddLimitedRoles).parallel().anyMatch(role.getName()::equalsIgnoreCase))
                        {
                            userHasLimitedAdd = true;
                        }
                    }

                    // check if user is already in the list if in limited add role
                    if(userHasLimitedAdd)
                    {
                        // create entry if user is not on list
                        if(DiscordWhitelister.getUserList().getString(author.getId()) == null)
                        {
                            DiscordWhitelister.getUserList().set(author.getId(), 0);

                            try
                            {
                                DiscordWhitelister.getUserList().save(DiscordWhitelister.getUserListFile().getPath());
                            }
                            catch (IOException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                if(messageContents.toLowerCase().equals("!whitelist") && userCanAddRemove || messageContents.toLowerCase().equals("!whitelist") && userCanAdd)
                {
                    if(userCanAddRemove || userCanAdd)
                    {
                        channel.sendMessage("```Discord Whitelister Bot For Spigot" + System.lineSeparator() +
                                "Version: 1.0.8" + System.lineSeparator() + "Links:" + System.lineSeparator() +
                                "https://www.spigotmc.org/resources/discord-whitelister.69929/" + System.lineSeparator() + "https://github.com/JoeShimo/DiscordWhitelisterBot-Spigot" + System.lineSeparator() +
                                "Commands:" + System.lineSeparator() + "Add:" + System.lineSeparator() +
                                "!whitelist add <MinecraftUsername> -- Usage: Adds a user to the whitelist" + System.lineSeparator() +
                                "Remove:" + System.lineSeparator() + "!whitelist remove <MinecraftUsername> -- Usage: Removes the target user from the whitelist" + System.lineSeparator() +
                                "If you encounter an issue, please report it here: https://github.com/JoeShimo/DiscordWhitelisterBot-Spigot/issues```").queue();
                    }
                }

                if(messageContents.toLowerCase().equals("!whitelist") && !author.isBot())
                {
                    boolean hasPerms = false;
                    if(userCanAddRemove || userCanAdd)
                    {
                        hasPerms = true;
                    }

                    // if the user doesn't have any allowed roles
                    if(!hasPerms)
                    {
                        channel.sendMessage(author.getAsMention() + ", you do not have permission to use this command").queue();
                    }
                }

                if(messageContents.toLowerCase().contains("!whitelist add"))
                {
                    boolean usedAllWhitelists = false;

                    if(DiscordWhitelister.getWhitelisterBotConfig().getBoolean("limited-whitelist-enabled") && userHasLimitedAdd && !userCanAddRemove && !userCanAdd)
                    {
                        if(DiscordWhitelister.getUserList().getString(author.getId()) != null
                                && Integer.parseInt(DiscordWhitelister.getUserList().getString(author.getId())) >= DiscordWhitelister.getWhitelisterBotConfig().getInt("max-whitelist-amount"))
                        {
                            usedAllWhitelists = true;
                        }
                    }

                    if(userCanAddRemove || userCanAdd || userHasLimitedAdd)
                    {
                        String nameToWhitelist = messageContents;
                        nameToWhitelist = nameToWhitelist.toLowerCase();
                        nameToWhitelist = nameToWhitelist.substring(nameToWhitelist.indexOf("!whitelist add")+14); // get everything after !whitelist add
                        nameToWhitelist = nameToWhitelist.replaceAll(" ", "");

                        final String finalNameToWhitelist = nameToWhitelist;

                        final char[] finalNameToWhitelistChar = finalNameToWhitelist.toCharArray();

                        if(userCanAddRemove || userCanAdd)
                        {
                            if(finalNameToWhitelist.isEmpty())
                            {
                                channel.sendMessage(author.getAsMention() + ", ```Whitelist Command:" + System.lineSeparator() +
                                        "!whitelist add <MinecraftUsername>" + System.lineSeparator() + "Usage: Adds a user to the whitelist" + System.lineSeparator() +
                                        "If you encounter an issue, please report it here: https://github.com/JoeShimo/DiscordWhitelisterBot-Spigot/issues```").queue();
                            }
                            else
                            {
                                if(DiscordWhitelister.getWhitelisterBotConfig().getBoolean("username-validation"))
                                {
                                    // invalid char check
                                    for(int a = 0; a < finalNameToWhitelistChar.length; ++a)
                                    {
                                        if(new String(validCharacters).indexOf(finalNameToWhitelistChar[a]) == -1)
                                        {
                                            channel.sendMessage(author.getAsMention() + ", the username you have specified contains invalid characters. **Only letters, numbers and underscores are allowed**.").queue();
                                            return;
                                        }
                                    }

                                    // length check
                                    if(finalNameToWhitelist.length() < 3 || finalNameToWhitelist.length() > 16)
                                    {
                                        channel.sendMessage(author.getAsMention() + ", the username you have specified either contains too few or too many characters. **Usernames can only consist of 3-16 characters**.").queue();
                                        return;
                                    }
                                }

                                // easy whitelist
                                FileConfiguration tempFileConfiguration = new YamlConfiguration();
                                // default whitelist
                                File whitelistJSON = (new File(".", "whitelist.json"));

                                DiscordWhitelister.getPlugin().getLogger().info(author.getName() + "("  + author.getId() + ") attempted to whitelist: " + finalNameToWhitelist);

                                if(DiscordWhitelister.useEasyWhitelist)
                                {
                                    try
                                    {
                                        tempFileConfiguration.load(new File(DiscordWhitelister.easyWhitelist.getDataFolder(), "config.yml"));
                                    }
                                    catch(IOException | InvalidConfigurationException e)
                                    {
                                        e.printStackTrace();
                                    }
                                }

                                boolean onWhitelist = false;

                                if(DiscordWhitelister.useEasyWhitelist)
                                {
                                    if(Objects.requireNonNull(tempFileConfiguration.getStringList("whitelisted")).contains(finalNameToWhitelist))
                                    {
                                        onWhitelist = true;
                                        channel.sendMessage(author.getAsMention() + ", user is already on the whitelist!").queue();
                                    }
                                }
                                else if(checkWhitelistJSON(whitelistJSON, finalNameToWhitelist))
                                {
                                    onWhitelist = true;
                                    channel.sendMessage(author.getAsMention() + ", user is already on the whitelist!").queue();
                                }

                                if(!onWhitelist)
                                {
                                    // remove from removed list
                                    if(DiscordWhitelister.getRemovedList().get(finalNameToWhitelist) != null)
                                    {
                                        DiscordWhitelister.getRemovedList().set(finalNameToWhitelist, null);

                                        try
                                        {
                                            DiscordWhitelister.getRemovedList().save(DiscordWhitelister.getRemovedListFile().getPath());
                                        }
                                        catch (IOException e)
                                        {
                                            e.printStackTrace();
                                        }

                                        DiscordWhitelister.getPlugin().getLogger().info(finalNameToWhitelist + " has been removed from the removed list by " + author.getName()
                                                + "(" + author.getId() + ")");
                                    }

                                    if(DiscordWhitelister.useEasyWhitelist)
                                    {
                                        DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), () -> DiscordWhitelister.getPlugin().getServer().dispatchCommand(DiscordWhitelister.getPlugin().getServer().getConsoleSender(),
                                                "easywl add " + finalNameToWhitelist));
                                    }
                                    else
                                    {
                                        DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), () -> DiscordWhitelister.getPlugin().getServer().dispatchCommand(DiscordWhitelister.getPlugin().getServer().getConsoleSender(),
                                                "whitelist add " + finalNameToWhitelist));
                                    }

                                    // run through the server so that the check doesn't execute before the server has had a chance to run the whitelist command -- unsure if this is the best way of doing this, but it works
                                    if(DiscordWhitelister.useEasyWhitelist)
                                    {
                                        DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), () ->
                                        {
                                            try
                                            {
                                                tempFileConfiguration.load(new File(DiscordWhitelister.easyWhitelist.getDataFolder(), "config.yml"));
                                            }
                                            catch(IOException | InvalidConfigurationException e)
                                            {
                                                e.printStackTrace();
                                            }

                                            if(Objects.requireNonNull(tempFileConfiguration.getStringList("whitelisted")).contains(finalNameToWhitelist))
                                            {
                                                channel.sendMessage(author.getAsMention() + ", successfully added `" + finalNameToWhitelist + "` to the whitelist.").queue();
                                            }
                                            else
                                            {
                                                channel.sendMessage(author.getAsMention() + ", failed to add `" + finalNameToWhitelist + "` to the whitelist, this is most likely due to an invalid Minecraft username.").queue();
                                            }
                                            return null;
                                        });
                                    }
                                    else
                                    {
                                        DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), () ->
                                        {
                                            if(checkWhitelistJSON(whitelistJSON, finalNameToWhitelist))
                                            {
                                                channel.sendMessage(author.getAsMention() + ", successfully added `" + finalNameToWhitelist + "` to the whitelist.").queue();
                                            }
                                            else
                                            {
                                                channel.sendMessage(author.getAsMention() + ", failed to add `" + finalNameToWhitelist + "` to the whitelist, this is most likely due to an invalid Minecraft username.").queue();
                                            }
                                            return null;
                                        });
                                    }
                                }
                            }
                        }

                        if(DiscordWhitelister.getWhitelisterBotConfig().getBoolean("limited-whitelist-enabled") && userHasLimitedAdd && !usedAllWhitelists && !userCanAddRemove && !userCanAdd)
                        {
                            if(DiscordWhitelister.getUserList().getString(author.getId()) != null)
                            {
                                int whitelistLimit = DiscordWhitelister.getWhitelisterBotConfig().getInt("max-whitelist-amount");
                                int timesWhitelisted = Integer.parseInt(DiscordWhitelister.getUserList().getString(author.getId()));

                                if(finalNameToWhitelist.isEmpty())
                                {
                                    channel.sendMessage(author.getAsMention() + ", ```Whitelist Command:" + System.lineSeparator() +
                                            "!whitelist add <MinecraftUsername>" + System.lineSeparator() + "Usage: Adds a user to the whitelist" + "```" + System.lineSeparator()
                                    + " You have **" + (whitelistLimit - timesWhitelisted)
                                    + " out of " + DiscordWhitelister.getWhitelisterBotConfig().getString("max-whitelist-amount") + "** whitelists remaining").queue();
                                }
                                else
                                {
                                    if(DiscordWhitelister.getWhitelisterBotConfig().getBoolean("username-validation"))
                                    {
                                        // invalid char check
                                        for(int a = 0; a < finalNameToWhitelistChar.length; ++a)
                                        {
                                            if(new String(validCharacters).indexOf(finalNameToWhitelistChar[a]) == -1)
                                            {
                                                channel.sendMessage(author.getAsMention() + ", the username you have specified contains invalid characters. **Only letters, numbers and underscores are allowed**."
                                                        + " You have **" + (whitelistLimit - timesWhitelisted)
                                                        + " out of " + DiscordWhitelister.getWhitelisterBotConfig().getString("max-whitelist-amount") + "** whitelists remaining.").queue();
                                                return;
                                            }
                                        }

                                        // length check
                                        if(finalNameToWhitelist.length() < 3 || finalNameToWhitelist.length() > 16)
                                        {
                                            channel.sendMessage(author.getAsMention() + ", the username you have specified either contains too few or too many characters. **Usernames can only consist of 3-16 characters**."
                                                    + " You have **" + (whitelistLimit - timesWhitelisted)
                                                    + " out of " + DiscordWhitelister.getWhitelisterBotConfig().getString("max-whitelist-amount") + "** whitelists remaining.").queue();
                                            return;
                                        }
                                    }

                                    // easy whitelist
                                    FileConfiguration tempFileConfiguration = new YamlConfiguration();
                                    // default whitelist
                                    File whitelistJSON = (new File(".", "whitelist.json"));

                                    DiscordWhitelister.getPlugin().getLogger().info(author.getName() + "("  + author.getId() + ") attempted to whitelist: " + finalNameToWhitelist
                                            + ", " + (whitelistLimit - timesWhitelisted) + " whitelists remaining");

                                    if(DiscordWhitelister.useEasyWhitelist)
                                    {
                                        try
                                        {
                                            tempFileConfiguration.load(new File(DiscordWhitelister.easyWhitelist.getDataFolder(), "config.yml"));
                                        }
                                        catch(IOException | InvalidConfigurationException e)
                                        {
                                            e.printStackTrace();
                                        }
                                    }

                                    boolean onWhitelist = false;

                                    if(DiscordWhitelister.useEasyWhitelist)
                                    {
                                        if(Objects.requireNonNull(tempFileConfiguration.getStringList("whitelisted")).contains(finalNameToWhitelist))
                                        {
                                            channel.sendMessage(author.getAsMention() + ", user is already on the whitelist!").queue();
                                            onWhitelist = true;
                                        }
                                    }
                                    else if(checkWhitelistJSON(whitelistJSON, finalNameToWhitelist))
                                    {
                                        onWhitelist = true;
                                        channel.sendMessage(author.getAsMention() + ", user is already on the whitelist!").queue();
                                    }

                                    if(!onWhitelist)
                                    {
                                        if(DiscordWhitelister.getRemovedList().get(finalNameToWhitelist) != null)
                                        {
                                            channel.sendMessage(author.getAsMention() + ", cannot add `" + finalNameToWhitelist + "` as this user was previously removed by a staff member (<@"
                                                    + DiscordWhitelister.getRemovedList().get(finalNameToWhitelist) + ">). Please ask a user with higher permissions to add this user."
                                                    + " You have **" + (whitelistLimit - timesWhitelisted)
                                                    + " out of " + DiscordWhitelister.getWhitelisterBotConfig().getString("max-whitelist-amount") + "** whitelists remaining.").queue();
                                        }
                                        else
                                        {
                                            if(DiscordWhitelister.useEasyWhitelist)
                                            {
                                                DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), () -> DiscordWhitelister.getPlugin().getServer().dispatchCommand(DiscordWhitelister.getPlugin().getServer().getConsoleSender(),
                                                        "easywl add " + finalNameToWhitelist));
                                            }
                                            else
                                            {
                                                DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), () -> DiscordWhitelister.getPlugin().getServer().dispatchCommand(DiscordWhitelister.getPlugin().getServer().getConsoleSender(),
                                                        "whitelist add " + finalNameToWhitelist));
                                            }

                                            int tempFinal = timesWhitelisted;

                                            if(tempFinal < 3)
                                            {
                                                tempFinal = timesWhitelisted + 1;
                                            }

                                            int finalTimesWhitelistedInc = tempFinal;

                                            int successfulFinalTimesWhitelisted = whitelistLimit - finalTimesWhitelistedInc;
                                            int failedFinalTimesWhitelisted = whitelistLimit - timesWhitelisted;

                                            // run through the server so that the check doesn't execute before the server has had a chance to run the whitelist command -- unsure if this is the best way of doing this, but it works
                                            if(DiscordWhitelister.useEasyWhitelist)
                                            {
                                                DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), () ->
                                                {
                                                    try
                                                    {
                                                        tempFileConfiguration.load(new File(DiscordWhitelister.easyWhitelist.getDataFolder(), "config.yml"));
                                                    }
                                                    catch(IOException | InvalidConfigurationException e)
                                                    {
                                                        e.printStackTrace();
                                                    }

                                                    if(Objects.requireNonNull(tempFileConfiguration.getStringList("whitelisted")).contains(finalNameToWhitelist))
                                                    {
                                                        channel.sendMessage(author.getAsMention() + ", successfully added `" + finalNameToWhitelist + "` to the whitelist."
                                                                + " You have **" + successfulFinalTimesWhitelisted
                                                                + " out of " + DiscordWhitelister.getWhitelisterBotConfig().getString("max-whitelist-amount") + "** whitelists remaining").queue();

                                                        DiscordWhitelister.getUserList().set(author.getId(), finalTimesWhitelistedInc);

                                                        try
                                                        {
                                                            DiscordWhitelister.getUserList().save(DiscordWhitelister.getUserListFile().getPath());
                                                        }
                                                        catch (IOException e)
                                                        {
                                                            e.printStackTrace();
                                                        }

                                                        DiscordWhitelister.getPlugin().getLogger().info(author.getName() + "("  + author.getId() + ") successfully added " + finalNameToWhitelist
                                                                + " to the whitelist, " + successfulFinalTimesWhitelisted + " whitelists remaining.");
                                                    }
                                                    else
                                                    {
                                                        channel.sendMessage(author.getAsMention() + ", failed to add `" + finalNameToWhitelist + "` to the whitelist, this is most likely due to an invalid Minecraft username."
                                                                + " You have **" + failedFinalTimesWhitelisted
                                                                + " out of " + DiscordWhitelister.getWhitelisterBotConfig().getString("max-whitelist-amount") + "** whitelists remaining.").queue();
                                                    }
                                                    return null;
                                                });
                                            }
                                            else
                                            {
                                                DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), () ->
                                                {
                                                    if(checkWhitelistJSON(whitelistJSON, finalNameToWhitelist))
                                                    {
                                                        channel.sendMessage(author.getAsMention() + ", successfully added `" + finalNameToWhitelist + "` to the whitelist."
                                                                + " You have **" + successfulFinalTimesWhitelisted
                                                                + " out of " + DiscordWhitelister.getWhitelisterBotConfig().getString("max-whitelist-amount") + "** whitelists remaining").queue();

                                                        DiscordWhitelister.getUserList().set(author.getId(), finalTimesWhitelistedInc);

                                                        try
                                                        {
                                                            DiscordWhitelister.getUserList().save(DiscordWhitelister.getUserListFile().getPath());
                                                        }
                                                        catch (IOException e)
                                                        {
                                                            e.printStackTrace();
                                                        }

                                                        DiscordWhitelister.getPlugin().getLogger().info(author.getName() + "("  + author.getId() + ") successfully added " + finalNameToWhitelist
                                                                + " to the whitelist, " + successfulFinalTimesWhitelisted + " whitelists remaining.");
                                                    }
                                                    else
                                                    {
                                                        channel.sendMessage(author.getAsMention() + ", failed to add `" + finalNameToWhitelist + "` to the whitelist, this is most likely due to an invalid Minecraft username."
                                                                + " You have **" + failedFinalTimesWhitelisted
                                                                + " out of " + DiscordWhitelister.getWhitelisterBotConfig().getString("max-whitelist-amount") + "** whitelists remaining.").queue();
                                                    }
                                                    return null;
                                                });
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else if(userHasLimitedAdd && usedAllWhitelists)
                        {
                            channel.sendMessage(author.getAsMention() + ", unable to whitelist. You have **" + (DiscordWhitelister.getWhitelisterBotConfig().getInt("max-whitelist-amount") - Integer.parseInt(DiscordWhitelister.getUserList().getString(author.getId())))
                            + " out of " + DiscordWhitelister.getWhitelisterBotConfig().getInt("max-whitelist-amount") + "** whitelists remaining.").queue();
                        }
                    }

                    if (messageContents.toLowerCase().contains("!whitelist add") && !author.isBot())
                    {
                        boolean hasPerms = false;
                        if(userCanAddRemove || userCanAdd || DiscordWhitelister.getWhitelisterBotConfig().getBoolean("limited-whitelist-enabled") && userHasLimitedAdd)
                        {
                            hasPerms = true;
                        }

                        // if the user doesn't have any allowed roles
                        if(!hasPerms)
                        {
                            channel.sendMessage(author.getAsMention() + ", you do not have permission to use this command").queue();
                        }
                    }
                }

                if(messageContents.toLowerCase().contains("!whitelist remove"))
                {
                    if(userCanAddRemove)
                    {
                        String nameToRemove = messageContents;
                        nameToRemove = nameToRemove.toLowerCase();
                        nameToRemove = nameToRemove.substring(nameToRemove.indexOf("!whitelist remove")+17); // get everything after !whitelist remove
                        nameToRemove = nameToRemove.replaceAll(" ", "");

                        final String finalNameToRemove = nameToRemove;

                        if(finalNameToRemove.isEmpty())
                        {
                            channel.sendMessage(author.getAsMention() + ", ```Whitelist Remove Command:" + System.lineSeparator() +
                                    "!whitelist remove <MinecraftUsername>" + System.lineSeparator() + "Usage: Removes the target user from the whitelist" + System.lineSeparator() +
                                    "If you encounter an issue, please report it here: https://github.com/JoeShimo/DiscordWhitelisterBot-Spigot/issues```").queue();
                        }
                        else
                        {
                            // easy whitelist
                            FileConfiguration tempFileConfiguration = new YamlConfiguration();
                            // default whitelist
                            File whitelistJSON = (new File(".", "whitelist.json"));

                            DiscordWhitelister.getPlugin().getLogger().info(author.getName() + "("  + author.getId() + ") attempted to remove " + finalNameToRemove + " from the whitelist");

                            if(DiscordWhitelister.useEasyWhitelist)
                            {
                                try
                                {
                                    tempFileConfiguration.load(new File(DiscordWhitelister.easyWhitelist.getDataFolder(), "config.yml"));
                                }
                                catch(IOException | InvalidConfigurationException e)
                                {
                                    e.printStackTrace();
                                }
                            }

                            boolean notOnWhitelist = false;

                            if(DiscordWhitelister.useEasyWhitelist)
                            {
                                if(!tempFileConfiguration.getStringList("whitelisted").contains(finalNameToRemove))
                                {
                                    channel.sendMessage(author.getAsMention() + ", `" + finalNameToRemove + "` is not on the whitelist!").queue();
                                    notOnWhitelist = true;
                                }
                            }

                            if(!DiscordWhitelister.useEasyWhitelist && !checkWhitelistJSON(whitelistJSON, finalNameToRemove))
                            {
                                notOnWhitelist = true;
                                channel.sendMessage(author.getAsMention() + ", `" + finalNameToRemove + "` is not on the whitelist!").queue();
                            }

                            if(!notOnWhitelist)
                            {
                                if(DiscordWhitelister.useEasyWhitelist)
                                {
                                    try
                                    {
                                        tempFileConfiguration.load(new File(DiscordWhitelister.easyWhitelist.getDataFolder(), "config.yml"));
                                    }
                                    catch(IOException | InvalidConfigurationException e)
                                    {
                                        e.printStackTrace();
                                    }

                                    DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), () -> DiscordWhitelister.getPlugin().getServer().dispatchCommand(DiscordWhitelister.getPlugin().getServer().getConsoleSender(),
                                            "easywl remove " + finalNameToRemove));
                                }
                                else
                                {
                                    DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), () -> DiscordWhitelister.getPlugin().getServer().dispatchCommand(DiscordWhitelister.getPlugin().getServer().getConsoleSender(),
                                            "whitelist remove " + finalNameToRemove));
                                }

                                if(DiscordWhitelister.useEasyWhitelist)
                                {
                                    DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), () ->
                                    {
                                        try
                                        {
                                            tempFileConfiguration.load(new File(DiscordWhitelister.easyWhitelist.getDataFolder(), "config.yml"));
                                        }
                                        catch(IOException | InvalidConfigurationException e)
                                        {
                                            e.printStackTrace();
                                        }

                                        if(!tempFileConfiguration.getStringList("whitelisted").contains(finalNameToRemove))
                                        {
                                            channel.sendMessage(author.getAsMention() + ", successfully removed `" + finalNameToRemove + "` from the whitelist.").queue();

                                            // if the name is not on the list
                                            if(DiscordWhitelister.getRemovedList().get(finalNameToRemove) == null)
                                            {
                                                DiscordWhitelister.getRemovedList().set(finalNameToRemove, author.getId());
                                                DiscordWhitelister.getRemovedList().save(DiscordWhitelister.getRemovedListFile().getPath());
                                            }
                                        }
                                        else
                                        {
                                            channel.sendMessage(author.getAsMention() + ", failed to remove `" + finalNameToRemove + "` from the whitelist, this should never really happen, you may have to remove the player manually and report the issue.").queue();
                                        }
                                        return null;
                                    });
                                }
                                else
                                {
                                    DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), () ->
                                    {
                                        if(!checkWhitelistJSON(whitelistJSON, finalNameToRemove))
                                        {
                                            channel.sendMessage(author.getAsMention() + ", successfully removed `" + finalNameToRemove + "` from the whitelist.").queue();

                                            // if the name is not on the list
                                            if(DiscordWhitelister.getRemovedList().get(finalNameToRemove) == null)
                                            {
                                                DiscordWhitelister.getRemovedList().set(finalNameToRemove, author.getId());
                                                DiscordWhitelister.getRemovedList().save(DiscordWhitelister.getRemovedListFile().getPath());
                                            }
                                        }
                                        else
                                        {
                                            channel.sendMessage(author.getAsMention() + ", failed to remove `" + finalNameToRemove + "` from the whitelist, this should never really happen, you may have to remove the player manually and report the issue.").queue();
                                        }
                                        return null;
                                    });
                                }

                            }
                        }
                    }

                    if(userCanAdd && !userCanAddRemove)
                    {
                        channel.sendMessage(author.getAsMention() +
                                ", you only have permission to add people to the whitelist. To remove people from the whitelist you must be moved to the following roles: "
                                + DiscordWhitelister.getWhitelisterBotConfig().getList("add-remove-roles").toString() + "; or get the owner to move your role to 'add-remove-roles' in the config.").queue();
                    }

                    if(messageContents.toLowerCase().contains("!whitelist remove") && !author.isBot())
                    {
                        boolean hasPerms = false;
                        if(userCanAddRemove || userCanAdd)
                        {
                            hasPerms = true;
                        }

                        // if the user doesn't have any allowed roles
                        if(!hasPerms)
                        {
                            channel.sendMessage(author.getAsMention() + ", you do not have permission to use this command").queue();
                        }
                    }
                }
            }
        }
    }

    private boolean checkWhitelistJSON(File whitelistFile, String minecraftUsername)
    {
        boolean correctUsername = false;

        try
        {
            JSONParser jsonParser = new JSONParser();

            JSONArray jsonArray = (JSONArray)jsonParser.parse(new FileReader(whitelistFile));

            for(Object object : jsonArray)
            {
                JSONObject player = (JSONObject) object;

                String name = (String)player.get("name");
                name = name.toLowerCase();

                if(name.equals(minecraftUsername))
                {
                    correctUsername = true;
                }
            }
        }
        catch(IOException | ParseException e)
        {
            e.printStackTrace();
        }

        if(correctUsername)
        {
            return true;
        }
        else
        {
            return false;
        }
    }
}
