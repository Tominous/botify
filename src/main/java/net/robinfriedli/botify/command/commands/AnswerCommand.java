package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class AnswerCommand extends AbstractCommand {

    private AbstractCommand sourceCommand;

    public AnswerCommand(CommandContext context, CommandManager commandManager, String commandString) {
        super(context, commandManager, commandString, false, false, true,
            "Answer a question asked by the bot. Commands like the play command may ask you to specify what " +
                "track you meant if several options where found.");
    }

    @Override
    public void doRun() {
        getManager().getQuestion(getContext()).ifPresentOrElse(question -> {
            sourceCommand = question.getSourceCommand();

            Object option = question.get(getCommandBody());
            try {
                sourceCommand.withUserResponse(option);
                question.destroy();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CommandRuntimeException(e);
            }
        }, () -> {
            throw new InvalidCommandException("I don't remember asking you anything " + getContext().getUser().getName());
        });
    }

    @Override
    public void onSuccess() {
        sourceCommand.onSuccess();
    }
}