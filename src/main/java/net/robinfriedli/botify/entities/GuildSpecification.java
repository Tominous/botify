package net.robinfriedli.botify.entities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class GuildSpecification extends AbstractXmlElement {

    public GuildSpecification(String tagName, Map<String, ?> attributeMap, List<XmlElement> subElements, String textContent, Context context) {
        super(tagName, attributeMap, subElements, textContent, context);
    }

    public GuildSpecification(Guild guild, Context context) {
        this(guild.getName(), guild.getId(), context);
    }

    public GuildSpecification(String guildName, String guildId, Context context) {
        this(guildName, guildId, "", context);
    }

    public GuildSpecification(String guildName, String guildId, String botifyName, Context context) {
        super("guildSpecification", buildAttributeMap(guildName, guildId, botifyName), context);
    }

    // invoked by JXP
    @SuppressWarnings("unused")
    public GuildSpecification(Element element, Context context) {
        super(element, context);
    }

    // invoked by JXP
    @SuppressWarnings("unused")
    public GuildSpecification(Element element, List<XmlElement> subElements, Context context) {
        super(element, subElements, context);
    }

    private static Map<String, String> buildAttributeMap(String guildName, String guildId, String botifyName) {
        Map<String, String> attributeMap = new HashMap<>();
        attributeMap.put("guildName", guildName);
        attributeMap.put("guildId", guildId);
        attributeMap.put("botifyName", botifyName);
        return attributeMap;
    }

    @Nullable
    @Override
    public String getId() {
        return null;
    }

    public String getName() {
        return getAttribute("botifyName").getValue();
    }

    @Nullable
    public String getPrefix() {
        if (hasAttribute("prefix")) {
            return getAttribute("prefix").getValue();
        }

        return null;
    }

    public String getGuildName() {
        return getAttribute("guildName").getValue();
    }

    public String getGuildId() {
        return getAttribute("guildId").getValue();
    }

    public Guild getGuild(JDA jda) {
        return jda.getGuildById(getGuildId());
    }

}
