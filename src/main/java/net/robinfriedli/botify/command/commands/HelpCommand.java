package net.robinfriedli.botify.command.commands;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.LinkedHashMultimap;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Role;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.queries.Query;
import net.robinfriedli.stringlist.StringListImpl;

public class HelpCommand extends AbstractCommand {

    public HelpCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() {
        if (getCommandBody().isBlank()) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setColor(Color.decode("#1DB954"));
            embedBuilder.setTitle("**Commands:**");
            embedBuilder.appendDescription("To get help with a specific command just enter the name of the command. E.g. $botify help play.");

            sendMessage(getContext().getChannel(), embedBuilder.build());

            List<AbstractCommand> commands = getManager().getAllCommands(getContext());
            LinkedHashMultimap<Category, AbstractCommand> commandsByCategory = LinkedHashMultimap.create();
            for (AbstractCommand command : commands) {
                commandsByCategory.put(command.getCategory(), command);
            }

            List<Category> categories = commandsByCategory.keySet().stream().sorted(Comparator.comparingInt(Enum::ordinal)).collect(Collectors.toList());
            for (Category category : categories) {
                EmbedBuilder embedBuilderCategory = new EmbedBuilder();
                embedBuilderCategory.setTitle("**" + category.getName() + "**");
                embedBuilderCategory.setDescription(category.getDescription());
                embedBuilderCategory.setColor(Color.decode("#1DB954"));
                for (AbstractCommand command : commandsByCategory.get(category)) {
                    embedBuilderCategory.addField(command.getIdentifier(), command.getDescription(), false);
                }
                sendMessage(getContext().getChannel(), embedBuilderCategory.build());
            }

        } else {
            getManager().getCommand(getContext(), getCommandBody()).ifPresentOrElse(command -> {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("Command " + command.getIdentifier() + ":");
                embedBuilder.setDescription(command.getDescription());
                embedBuilder.setColor(Color.decode("#1DB954"));
                Guild guild = getContext().getGuild();
                AccessConfiguration accessConfiguration = getManager().getGuildManager().getAccessConfiguration(command.getIdentifier(), guild);
                if (accessConfiguration != null) {
                    String title = "Available to roles: ";
                    String text;
                    List<Role> roles = accessConfiguration.getRoles(guild);
                    if (!roles.isEmpty()) {
                        text = StringListImpl.create(roles, Role::getName).toSeparatedString(", ");
                    } else {
                        text = "Guild owner only";
                    }

                    embedBuilder.addField(title, text, false);
                }
                ArgumentContribution argumentContribution = command.setupArguments();
                if (!argumentContribution.isEmpty()) {
                    embedBuilder.addField("__Arguments__", "Keywords that alter the command behavior or define a search scope", false);

                    for (ArgumentContribution.Argument argument : argumentContribution.getArguments()) {
                        embedBuilder.addField("$" + argument.getArgument(), argument.getDescription(), false);
                    }

                }

                List<XmlElement> examples = Query
                    .evaluate(xmlElement -> xmlElement.getTagName().equals("example"))
                    .execute(command.getCommandContribution().getSubElements())
                    .collect();
                if (!examples.isEmpty()) {
                    embedBuilder.addField("__Examples__", "Practical usage examples for this command. Note that '$botify' can be exchanged for your custom prefix or bot name.", false);
                    for (XmlElement example : examples) {
                        embedBuilder.addField(example.getAttribute("title").getValue(), example.getTextContent(), false);
                    }
                }

                sendMessage(getContext().getChannel(), embedBuilder.build());
            }, () -> {
                throw new InvalidCommandException("No command found for " + getCommandBody());
            });
        }
    }

    @Override
    public void onSuccess() {
    }
}
