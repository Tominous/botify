package net.robinfriedli.botify.command.commands;

import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;

public class StopCommand extends AbstractCommand {

    public StopCommand(CommandContext context, CommandManager commandManager, String commandString) {
        super(context, commandManager, commandString, false, false, false,
            "Stop playback and empty the queue.");
    }

    @Override
    public void doRun() {
        Guild guild = getContext().getGuild();
        AudioManager audioManager = getManager().getAudioManager();
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        playback.stop();
        playback.getAudioQueue().clear();
        audioManager.leaveChannel(playback);
    }

    @Override
    public void onSuccess() {
    }
}