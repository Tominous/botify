package net.robinfriedli.botify.discord;

import java.io.File;
import java.util.List;

import com.google.common.base.Strings;
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandExecutor;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.exceptions.CommandExceptionHandler;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;

public class DiscordListener extends ListenerAdapter {

    private Mode mode;

    private final AudioManager audioManager;
    private final JxpBackend jxpBackend;
    private final Context defaultPlaylistContext;
    private final CommandManager commandManager;
    private final GuildSpecificationManager guildSpecificationManager;

    public DiscordListener(SpotifyApi spotifyApi, JxpBackend jxpBackend, LoginManager loginManager, YouTubeService youTubeService) {
        audioManager = new AudioManager(youTubeService);
        this.jxpBackend = jxpBackend;
        defaultPlaylistContext = jxpBackend.getContext(PropertiesLoadingService.requireProperty("PLAYLISTS_PATH"));
        Context guildSpecificationContext = jxpBackend.getContext(PropertiesLoadingService.requireProperty("GUILD_SPECIFICATION_PATH"));
        guildSpecificationManager = new GuildSpecificationManager(guildSpecificationContext);
        commandManager = new CommandManager(new CommandExecutor(spotifyApi, loginManager), this, loginManager, spotifyApi);
    }

    public void setupGuilds(Mode mode, List<Guild> guilds) {
        this.mode = mode;
        for (Guild guild : guilds) {
            if (mode == Mode.PARTITIONED) {
                setupGuildContext(guild);
            }
            guildSpecificationManager.addGuild(guild);
            audioManager.addGuild(guild);
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.getAuthor().isBot() && event.getGuild() != null) {
            Guild guild = event.getGuild();
            Message message = event.getMessage();
            String msg = message.getContentDisplay();
            String botName = guildSpecificationManager.getNameForGuild(guild);

            if ((!Strings.isNullOrEmpty(botName) && msg.toLowerCase().startsWith(botName.toLowerCase()))
                || msg.startsWith("$botify")) {
                // specify with which part of the input string the bot was referenced, this helps trimming the command later
                String namePrefix;
                if (msg.startsWith("$botify")) {
                    namePrefix = "$botify";
                } else {
                    namePrefix = botName;
                }

                CommandContext commandContext = new CommandContext(namePrefix, message);
                Thread commandExecutionThread = new Thread(() -> {
                    try {
                        commandManager.runCommand(commandContext);
                    } catch (InvalidCommandException | NoResultsFoundException e) {
                        message.getChannel().sendMessage(e.getMessage()).queue();
                    }
                });

                commandExecutionThread.setUncaughtExceptionHandler(new CommandExceptionHandler(commandContext));
                commandExecutionThread.setName("botify command execution: " + commandContext);
                commandExecutionThread.start();
            }
        }
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        guildSpecificationManager.addGuild(guild);
        audioManager.addGuild(guild);

        if (mode == Mode.PARTITIONED) {
            setupGuildContext(guild);
        }
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        Guild guild = event.getGuild();
        audioManager.removeGuild(guild);
    }

    public GuildSpecificationManager getGuildSpecificationManager() {
        return guildSpecificationManager;
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }

    public JxpBackend getJxpBackend() {
        return jxpBackend;
    }

    public Context getDefaultPlaylistContext() {
        return defaultPlaylistContext;
    }

    public Mode getMode() {
        return mode;
    }

    private void setupGuildContext(Guild guild) {
        String path = PropertiesLoadingService.requireProperty("GUILD_PLAYLISTS_PATH", guild.getId());
        File xmlFile = new File(path);
        if (xmlFile.exists()) {
            jxpBackend.createBoundContext(xmlFile, guild);
        } else {
            defaultPlaylistContext.copy(path, guild);
        }
    }

    public enum Mode {
        /**
         * all guilds share the same {@link Context} meaning all playlists will be available across all guilds
         */
        SHARED,

        /**
         * there will be a separate {@link Context.BindableContext} for each guild meaning the playlists will be separated
         * <p>
         * Recommended if one botify instance is shared by completely different guilds
         */
        PARTITIONED
    }

}