package net.robinfriedli.botify.command.commands;

import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.CommandContribution;

public class PauseCommand extends AbstractCommand {

    public PauseCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.PLAYBACK);
    }

    @Override
    public void doRun() {
        Guild guild = getContext().getGuild();
        AudioPlayback playback = getManager().getAudioManager().getPlaybackForGuild(guild);
        playback.pause();
    }

    @Override
    public void onSuccess() {
    }

}
