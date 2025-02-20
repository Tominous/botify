package net.robinfriedli.botify.command.commands;

import java.util.List;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.entities.PlaybackHistory;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.entities.Video;
import org.hibernate.Session;

public class AnalyticsCommand extends AbstractCommand {

    public AnalyticsCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() throws Exception {
        JDA jda = getContext().getJda();
        List<Guild> guilds = jda.getGuilds();
        AudioManager audioManager = getManager().getAudioManager();
        Session session = getContext().getSession();
        Runtime runtime = Runtime.getRuntime();

        int guildCount = guilds.size();
        long playingCount = guilds.stream().map(audioManager::getPlaybackForGuild).filter(AudioPlayback::isPlaying).count();
        long commandCount = session.createQuery("select count(*) from " + CommandHistory.class.getName(), Long.class).uniqueResult();
        long playlistCount = session.createQuery("select count(*) from " + Playlist.class.getName(), Long.class).uniqueResult();
        long trackCount = session.createQuery("select count(*) from " + Song.class.getName(), Long.class).uniqueResult()
            + session.createQuery("select count(*) from " + Video.class.getName(), Long.class).uniqueResult()
            + session.createQuery("select count(*) from " + UrlTrack.class.getName(), Long.class).uniqueResult();
        long playedCount = session.createQuery("select count(*) from " + PlaybackHistory.class.getName(), Long.class).uniqueResult();
        double maxMemory = runtime.maxMemory() / Math.pow(1024, 2);
        double allocatedMemory = runtime.totalMemory() / Math.pow(1024, 2);
        double unallocatedMemory = maxMemory - allocatedMemory;
        double allocFreeMemory = runtime.freeMemory() / Math.pow(1024, 2);
        double usedMemory = allocatedMemory - allocFreeMemory;
        double totalFreeMemory = maxMemory - usedMemory;

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.addField("Guilds", String.valueOf(guildCount), true);
        embedBuilder.addField("Guilds playing now", String.valueOf(playingCount), true);
        embedBuilder.addField("Total commands entered", String.valueOf(commandCount), true);
        embedBuilder.addField("Saved playlists", String.valueOf(playlistCount), true);
        embedBuilder.addField("Saved tracks", String.valueOf(trackCount), true);
        embedBuilder.addField("Total tracks played", String.valueOf(playedCount), true);
        embedBuilder.addField("Memory (in MB)",
            "Total: " + maxMemory + System.lineSeparator() +
                "Allocated: " + allocatedMemory + System.lineSeparator() +
                "Unallocated: " + unallocatedMemory + System.lineSeparator() +
                "Free allocated: " + allocFreeMemory + System.lineSeparator() +
                "Currently used: " + usedMemory + System.lineSeparator() +
                "Total free: " + totalFreeMemory
            , true);

        sendWithLogo(getContext().getChannel(), embedBuilder);
    }

    @Override
    public void onSuccess() {
    }
}
