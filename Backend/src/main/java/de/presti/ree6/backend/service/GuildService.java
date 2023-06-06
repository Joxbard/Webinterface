package de.presti.ree6.backend.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.presti.ree6.backend.utils.data.container.*;
import de.presti.ree6.backend.utils.data.container.api.GenericNotifierRequest;
import de.presti.ree6.backend.utils.data.container.guild.GuildContainer;
import de.presti.ree6.backend.utils.data.container.guild.GuildStatsContainer;
import de.presti.ree6.backend.utils.data.container.role.RoleLevelContainer;
import de.presti.ree6.sql.SQLSession;
import de.presti.ree6.sql.entities.*;
import de.presti.ree6.sql.entities.custom.CustomCommand;
import de.presti.ree6.sql.entities.webhook.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GuildService {

    private final SessionService sessionService;

    @Autowired
    public GuildService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    //region Stats

    public GuildStatsContainer getStats(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        return new GuildStatsContainer(SQLSession.getSqlConnector().getSqlWorker().getInvites(guildId).size(),
                SQLSession.getSqlConnector().getSqlWorker().getStats(guildId).stream().map(CommandStatsContainer::new).toList());
    }

    public List<CommandStatsContainer> getCommandStats(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        return SQLSession.getSqlConnector().getSqlWorker().getStats(guildId).stream().map(CommandStatsContainer::new).toList();
    }

    public int getInviteCount(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        return SQLSession.getSqlConnector().getSqlWorker().getInvites(guildId).size();
    }

    //endregion

    //region Log channel

    public ChannelContainer getLogChannel(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true);
        WebhookLog webhook = SQLSession.getSqlConnector().getSqlWorker().getLogWebhook(guildId);
        if (webhook == null) {
            return new ChannelContainer();
        }

        if (webhook.getChannelId() != 0) {
            return new ChannelContainer(guildContainer.getGuildChannelById(String.valueOf(webhook.getChannelId())));
        } else {
            net.dv8tion.jda.api.entities.Webhook webhook1 = guildContainer.getGuild().retrieveWebhooks().complete().stream()
                    .filter(entry -> entry.getId().equalsIgnoreCase(webhook.getWebhookId()) && entry.getToken().equalsIgnoreCase(webhook.getToken())).findFirst().orElse(null);

            if (webhook1 != null) {
                webhook.setChannelId(webhook1.getChannel().getIdLong());
                SQLSession.getSqlConnector().getSqlWorker().updateEntity(webhook);
                return new ChannelContainer(webhook1);
            }
        }

        return new ChannelContainer();
    }

    public void updateLogChannel(String sessionIdentifier, String guildId, String channelId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        Guild guild = guildContainer.getGuild();
        StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class, channelId);

        net.dv8tion.jda.api.entities.Webhook newWebhook = channel.createWebhook("Ree6-Log").complete();

        WebhookWelcome welcome = deleteWelcomeChannel(guild);

        SQLSession.getSqlConnector().getSqlWorker().setLogWebhook(guildId, channel.getIdLong(), newWebhook.getId(), newWebhook.getToken());
    }

    public WebhookLog removeLogChannel(String sessionIdentifier, String guildId) throws IllegalAccessException {
        return deleteLogChannel(sessionService.retrieveGuild(sessionIdentifier, guildId).getGuild());
    }

    private WebhookLog deleteLogChannel(Guild guild) {
        WebhookLog webhook = SQLSession.getSqlConnector().getSqlWorker().getLogWebhook(guild.getId());

        if (webhook != null) {
            guild.retrieveWebhooks().queue(c -> c.stream().filter(entry -> entry.getToken() != null)
                    .filter(entry -> entry.getId().equalsIgnoreCase(webhook.getWebhookId()) && entry.getToken().equalsIgnoreCase(webhook.getToken()))
                    .forEach(entry -> entry.delete().queue()));
        }

        return webhook;
    }

    //endregion

    //region Welcome channel

    public ChannelContainer getWelcomeChannel(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true);
        WebhookWelcome webhook = SQLSession.getSqlConnector().getSqlWorker().getWelcomeWebhook(guildId);
        if (webhook == null) {
            return new ChannelContainer();
        }

        if (webhook.getChannelId() != 0) {
            return new ChannelContainer(guildContainer.getGuildChannelById(String.valueOf(webhook.getChannelId())));
        } else {
            net.dv8tion.jda.api.entities.Webhook webhook1 = guildContainer.getGuild().retrieveWebhooks().complete().stream()
                    .filter(entry -> entry.getId().equalsIgnoreCase(webhook.getWebhookId()) && entry.getToken().equalsIgnoreCase(webhook.getToken())).findFirst().orElse(null);

            if (webhook1 != null) {
                webhook.setChannelId(webhook1.getChannel().getIdLong());
                SQLSession.getSqlConnector().getSqlWorker().updateEntity(webhook);
                return new ChannelContainer(webhook1);
            }
        }

        return new ChannelContainer();
    }

    public void updateWelcomeChannel(String sessionIdentifier, String guildId, String channelId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        Guild guild = guildContainer.getGuild();

        StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class, channelId);

        WebhookWelcome welcome = deleteWelcomeChannel(guild);

        net.dv8tion.jda.api.entities.Webhook newWebhook = channel.createWebhook("Ree6-Welcome").complete();

        SQLSession.getSqlConnector().getSqlWorker().setWelcomeWebhook(guildId, channel.getIdLong(), newWebhook.getId(), newWebhook.getToken());
    }

    public WebhookWelcome removeWelcomeChannel(String sessionIdentifier, String guildId) throws IllegalAccessException {
        return deleteWelcomeChannel(sessionService.retrieveGuild(sessionIdentifier, guildId).getGuild());
    }

    private WebhookWelcome deleteWelcomeChannel(Guild guild) {
        WebhookWelcome webhook = SQLSession.getSqlConnector().getSqlWorker().getWelcomeWebhook(guild.getId());

        if (webhook != null) {
            guild.retrieveWebhooks().queue(c -> c.stream().filter(entry -> entry.getToken() != null)
                    .filter(entry -> entry.getId().equalsIgnoreCase(webhook.getWebhookId()) && entry.getToken().equalsIgnoreCase(webhook.getToken()))
                    .forEach(entry -> entry.delete().queue()));
        }

        return webhook;
    }

    //endregion

    //region Notifier

    //region Reddit Notifications

    public List<NotifierContainer> getRedditNotifier(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        List<WebhookReddit> subreddits = SQLSession.getSqlConnector().getSqlWorker().getAllRedditWebhooks(guildId);

        return subreddits.stream().map(subreddit -> new NotifierContainer(subreddit.getSubreddit(), subreddit.getMessage(), guildContainer.getGuild().retrieveWebhooks()
                .complete().stream().filter(c -> c.getId().equals(subreddit.getGuildId())).map(ChannelContainer::new).findFirst().orElse(null))).toList();
    }

    public void addRedditNotifier(String sessionIdentifier, String guildId, GenericNotifierRequest notifierRequest) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true);
        Guild guild = guildContainer.getGuild();
        StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class, notifierRequest.channelId());

        net.dv8tion.jda.api.entities.Webhook newWebhook = channel.createWebhook("Ree6-RedditNotifier-" + notifierRequest.name()).complete();

        SQLSession.getSqlConnector().getSqlWorker().addRedditWebhook(guildId, channel.getIdLong(), newWebhook.getId(), newWebhook.getToken(),
                notifierRequest.name(), notifierRequest.message());
    }

    // TODO:: make a universal delete method for webhooks, safe code.
    public void removeRedditNotifier(String sessionIdentifier, String guildId, String subreddit) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        SQLSession.getSqlConnector().getSqlWorker().removeRedditWebhook(guildId, subreddit);
    }

    //endregion

    //region Twitch Notifications

    public List<NotifierContainer> getTwitchNotifier(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        List<WebhookTwitch> twitchChannels = SQLSession.getSqlConnector().getSqlWorker().getAllTwitchWebhooks(guildId);

        return twitchChannels.stream().map(twitchChannel -> new NotifierContainer(twitchChannel.getName(), twitchChannel.getMessage(), guildContainer.getGuild().retrieveWebhooks()
                .complete().stream().filter(c -> c.getId().equals(twitchChannel.getGuildId())).map(ChannelContainer::new).findFirst().orElse(null))).toList();
    }

    public void addTwitchNotifier(String sessionIdentifier, String guildId, GenericNotifierRequest notifierRequest) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true);
        Guild guild = guildContainer.getGuild();
        StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class, notifierRequest.channelId());

        net.dv8tion.jda.api.entities.Webhook newWebhook = channel.createWebhook("Ree6-TwitchNotifier-" + notifierRequest.name()).complete();

        SQLSession.getSqlConnector().getSqlWorker().addTwitchWebhook(guildId, channel.getIdLong(), newWebhook.getId(), newWebhook.getToken(),
                notifierRequest.name(), notifierRequest.message());
    }

    public void removeTwitchNotifier(String sessionIdentifier, String guildId, String channelId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        SQLSession.getSqlConnector().getSqlWorker().removeTwitchWebhook(guildId, channelId);
    }

    //endregion

    //region YouTube Notifications

    public List<NotifierContainer> getYouTubeNotifier(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        List<WebhookYouTube> youtubers = SQLSession.getSqlConnector().getSqlWorker().getAllYouTubeWebhooks(guildId);

        return youtubers.stream().map(youtuber -> new NotifierContainer(youtuber.getName(), youtuber.getMessage(), guildContainer.getGuild().retrieveWebhooks()
                .complete().stream().filter(c -> c.getId().equals(youtuber.getGuildId())).map(ChannelContainer::new).findFirst().orElse(null))).toList();
    }

    public void addYouTubeNotifier(String sessionIdentifier, String guildId, GenericNotifierRequest notifierRequest) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true);
        Guild guild = guildContainer.getGuild();
        StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class, notifierRequest.channelId());

        net.dv8tion.jda.api.entities.Webhook newWebhook = channel.createWebhook("Ree6-YoutubeNotifier-" + notifierRequest.name()).complete();

        SQLSession.getSqlConnector().getSqlWorker().addYouTubeWebhook(guildId, channel.getIdLong(), newWebhook.getId(), newWebhook.getToken(),
                notifierRequest.name(), notifierRequest.message());
    }

    public void removeYouTubeNotifier(String sessionIdentifier, String guildId, String channelId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        SQLSession.getSqlConnector().getSqlWorker().removeYouTubeWebhook(guildId, channelId);
    }

    //endregion

    //region Twitter Notifications

    public List<NotifierContainer> getTwitterNotifier(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        List<WebhookTwitter> twitterUsers = SQLSession.getSqlConnector().getSqlWorker().getAllTwitterWebhooks(guildId);

        return twitterUsers.stream().map(twitterUser -> new NotifierContainer(twitterUser.getName(), twitterUser.getMessage(), guildContainer.getGuild().retrieveWebhooks()
                .complete().stream().filter(c -> c.getId().equals(twitterUser.getGuildId())).map(ChannelContainer::new).findFirst().orElse(null))).toList();
    }

    public void addTwitterNotifier(String sessionIdentifier, String guildId, GenericNotifierRequest notifierRequest) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true);
        Guild guild = guildContainer.getGuild();
        StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class, notifierRequest.channelId());

        net.dv8tion.jda.api.entities.Webhook newWebhook = channel.createWebhook("Ree6-TwitterNotifier-" + notifierRequest.name()).complete();

        SQLSession.getSqlConnector().getSqlWorker().addTwitterWebhook(guildId, channel.getIdLong(), newWebhook.getId(), newWebhook.getToken(),
                notifierRequest.name(), notifierRequest.message());
    }

    public void removeTwitterNotifier(String sessionIdentifier, String guildId, String name) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        SQLSession.getSqlConnector().getSqlWorker().removeTwitterWebhook(guildId, name);
    }

    //endregion

    //region Instagram Notifications

    public List<NotifierContainer> getInstagramNotifier(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        List<WebhookInstagram> instagramUsers = SQLSession.getSqlConnector().getSqlWorker().getAllInstagramWebhooks(guildId);

        return instagramUsers.stream().map(instagramUser -> new NotifierContainer(instagramUser.getName(), instagramUser.getMessage(), guildContainer.getGuild().retrieveWebhooks()
                .complete().stream().filter(c -> c.getId().equals(instagramUser.getGuildId())).map(ChannelContainer::new).findFirst().orElse(null))).toList();
    }

    public void addInstagramNotifier(String sessionIdentifier, String guildId, GenericNotifierRequest notifierRequest) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true);
        Guild guild = guildContainer.getGuild();
        StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class, notifierRequest.channelId());

        net.dv8tion.jda.api.entities.Webhook newWebhook = channel.createWebhook("Ree6-InstagramNotifier-" + notifierRequest.name()).complete();

        SQLSession.getSqlConnector().getSqlWorker().addInstagramWebhook(guildId, channel.getIdLong(), newWebhook.getId(), newWebhook.getToken(),
                notifierRequest.name(), notifierRequest.message());
    }

    public void removeInstagramNotifier(String sessionIdentifier, String guildId, String name) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId);
        SQLSession.getSqlConnector().getSqlWorker().removeInstagramWebhook(guildId, name);
    }

    //endregion

    //endregion

    //region LevelRewards

    //region Chat

    public List<RoleLevelContainer> getChatAutoRoles(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, true);
        return SQLSession.getSqlConnector().getSqlWorker().getChatLevelRewards(guildId).entrySet().stream().map(x -> new RoleLevelContainer(x.getKey(), guildContainer.getRoleById(x.getValue()))).toList();
    }

    public void addChatAutoRole(String sessionIdentifier, String guildId, String roleId, long level) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);

        if (guildContainer.getRoleById(roleId) == null)
            throw new IllegalAccessException("Role not found");

        SQLSession.getSqlConnector().getSqlWorker().addChatLevelReward(guildId, roleId, level);
    }

    public void removeChatAutoRole(String sessionIdentifier, String guildId, long level) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);
        SQLSession.getSqlConnector().getSqlWorker().removeChatLevelReward(guildId, level);
    }

    //endregion

    //region Voice

    public List<RoleLevelContainer> getVoiceAutoRoles(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, true);
        return SQLSession.getSqlConnector().getSqlWorker().getVoiceLevelRewards(guildId).entrySet().stream().map(x -> new RoleLevelContainer(x.getKey(), guildContainer.getRoleById(x.getValue()))).toList();
    }

    public void addVoiceAutoRole(String sessionIdentifier, String guildId, String roleId, long level) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, true);

        if (guildContainer.getRoleById(roleId) == null)
            throw new IllegalAccessException("Role not found");

        SQLSession.getSqlConnector().getSqlWorker().addVoiceLevelReward(guildId, roleId, level);
    }

    public void removeVoiceAutoRole(String sessionIdentifier, String guildId, long level) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);
        SQLSession.getSqlConnector().getSqlWorker().removeVoiceLevelReward(guildId, level);
    }

    //endregion

    //endregion

    public RecordContainer getRecording(String sessionIdentifier, String recordId) throws IllegalAccessException {
        SessionContainer sessionContainer = sessionService.retrieveSession(sessionIdentifier);
        List<GuildContainer> guilds = sessionService.retrieveGuilds(sessionIdentifier, false);

        Recording recording = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Recording(), "SELECT * FROM Recording WHERE ID=:id", Map.of("id", recordId));

        if (guilds.stream().anyMatch(g -> g.getId().equalsIgnoreCase(recording.getGuildId()))) {
            boolean found = false;

            for (JsonElement element : recording.getJsonArray()) {
                if (element.isJsonPrimitive()) {
                    JsonPrimitive primitive = element.getAsJsonPrimitive();
                    if (primitive.isString() && primitive.getAsString().equalsIgnoreCase(sessionContainer.getUser().getId())) {
                        found = true;
                        break;
                    }
                }
            }

            if (found) {
                SQLSession.getSqlConnector().getSqlWorker().deleteEntity(recording);
                return new RecordContainer(recording);
            } else {
                throw new IllegalAccessException("You were not part of this recording.");
            }
        } else {
            throw new IllegalAccessException("You were not part of the Guild this recording was made in!");
        }
    }

    //region Temporal Voice

    public ChannelContainer getTemporalVoice(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true, false);
        TemporalVoicechannel temporalVoicechannel = SQLSession.getSqlConnector().getSqlWorker()
                .getEntity(new TemporalVoicechannel(), "SELECT * FROM TemporalVoicechannel WHERE GID=:gid", Map.of("gid", guildId));

        return guildContainer.getChannelById(temporalVoicechannel.getVoiceChannelId());
    }

    public void updateTemporalVoice(String sessionIdentifier, String guildId, String channelId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true, false);

        if (guildContainer.getChannelById(channelId) == null)
            throw new IllegalAccessException("Channel not found");

        TemporalVoicechannel temporalVoicechannel = SQLSession.getSqlConnector().getSqlWorker()
                .getEntity(new TemporalVoicechannel(), "SELECT * FROM TemporalVoicechannel WHERE GID=:gid", Map.of("gid", guildId));

        if (temporalVoicechannel != null) {
            temporalVoicechannel.setVoiceChannelId(channelId);
        } else {
            temporalVoicechannel = new TemporalVoicechannel(guildId, channelId);
        }

        SQLSession.getSqlConnector().getSqlWorker().updateEntity(temporalVoicechannel);
    }

    public void removeTemporalVoice(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);

        TemporalVoicechannel temporalVoicechannel = SQLSession.getSqlConnector().getSqlWorker()
                .getEntity(new TemporalVoicechannel(), "SELECT * FROM TemporalVoicechannel WHERE GID=:gid", Map.of("gid", guildId));

        if (temporalVoicechannel != null) {
            SQLSession.getSqlConnector().getSqlWorker().deleteEntity(temporalVoicechannel);
        }
    }

    //endregion

    //region OptOut

    public String checkOptOut(String sessionIdentifier, String guildId) throws IllegalAccessException {
        SessionContainer sessionContainer = sessionService.retrieveSession(sessionIdentifier);
        return SQLSession.getSqlConnector().getSqlWorker().isOptOut(guildId, sessionContainer.getUser().getId()) ? "optedOut" : "optedIn";
    }

    public String optOut(String sessionIdentifier, String guildId) throws IllegalAccessException {
        SessionContainer sessionContainer = sessionService.retrieveSession(sessionIdentifier);
        if (SQLSession.getSqlConnector().getSqlWorker().isOptOut(guildId, sessionContainer.getUser().getId())) {
            SQLSession.getSqlConnector().getSqlWorker().optOut(guildId, sessionContainer.getUser().getId());
            return "Opted out!";
        } else {
            SQLSession.getSqlConnector().getSqlWorker().optIn(guildId, sessionContainer.getUser().getId());
            return "Opted in!";
        }
    }

    //endregion

    //region Ticket

    public TicketContainer getTicket(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true, false);
        Tickets tickets = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Tickets(), "SELECT * FROM Tickets WHERE GUILDID=:gid", Map.of("gid", guildId));

        TicketContainer ticketContainer = new TicketContainer();
        ticketContainer.setTicketCount(tickets.getTicketCount());
        ticketContainer.setChannel(guildContainer.getChannelById(String.valueOf(tickets.getChannelId())));
        ticketContainer.setCategory(guildContainer.getCategoryById(String.valueOf(tickets.getTicketCategory())));

        StandardGuildMessageChannel logChannel = guildContainer.getGuild().getChannelById(StandardGuildMessageChannel.class, tickets.getLogChannelId());
        ticketContainer.setLogChannel(new ChannelContainer(logChannel));
        ticketContainer.setTicketOpenMessage(SQLSession.getSqlConnector().getSqlWorker().getSetting(guildId, "message_ticket_open").getStringValue());
        ticketContainer.setTicketMenuMessage(SQLSession.getSqlConnector().getSqlWorker().getSetting(guildId, "message_ticket_menu").getStringValue());

        return ticketContainer;
    }

    public void updateTicket(String sessionIdentifier, String guildId, String channelId, String logChannelId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true, false);

        Guild guild = guildContainer.getGuild();

        Tickets tickets = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Tickets(),
                "SELECT * FROM Tickets WHERE GUILDID=:gid", Map.of("gid", guildId));

        boolean requireChannel = false;

        if (tickets == null) {
            tickets = new Tickets();
            tickets.setGuildId(Long.valueOf(guildId));
            requireChannel = true;
        }

        if (channelId != null) {
            if (guildContainer.getChannelById(channelId) == null)
                throw new IllegalAccessException("Channel not found");

            tickets.setChannelId(Long.parseLong(channelId));
        } else if (requireChannel) {
            throw new IllegalAccessException("Channel not found");
        }

        if (logChannelId != null) {
            StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class, logChannelId);

            Tickets finalTickets = tickets;
            guild.retrieveWebhooks().queue(c -> c.stream().filter(entry -> entry.getToken() != null)
                    .filter(entry -> entry.getIdLong() == finalTickets.getLogChannelId() && entry.getToken().equalsIgnoreCase(finalTickets.getLogChannelWebhookToken()))
                    .forEach(entry -> entry.delete().queue()));

            net.dv8tion.jda.api.entities.Webhook newWebhook = channel.createWebhook("Ticket-Log").complete();
            tickets.setLogChannelWebhookToken(newWebhook.getToken());
            tickets.setLogChannelId(newWebhook.getIdLong());
        }

        SQLSession.getSqlConnector().getSqlWorker().updateEntity(tickets);
    }

    public void removeTicket(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);

        Tickets tickets = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Tickets(),
                "SELECT * FROM Tickets WHERE GUILDID=:gid", Map.of("gid", guildId));

        if (tickets != null) {
            guildContainer.getGuild().retrieveWebhooks().queue(c -> c.stream().filter(entry -> entry.getToken() != null)
                    .filter(entry -> entry.getIdLong() == tickets.getLogChannelId() && entry.getToken().equalsIgnoreCase(tickets.getLogChannelWebhookToken()))
                    .forEach(entry -> entry.delete().queue()));

            SQLSession.getSqlConnector().getSqlWorker().deleteEntity(tickets);
        }
    }

    //endregion

    //region Suggestion

    public ChannelContainer getSuggestion(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true, false);

        Suggestions suggestions = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Suggestions(),
                "SELECT * FROM Suggestions WHERE guildId = :id", Map.of("id", guildId));

        return guildContainer.getChannelById(String.valueOf(suggestions.getChannelId()));
    }

    public void updateSuggestion(String sessionIdentifier, String guildId, String channelId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true, false);

        Guild guild = guildContainer.getGuild();

        Suggestions suggestions = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Suggestions(),
                "SELECT * FROM Suggestions WHERE guildId = :id", Map.of("id", guildId));

        boolean requireChannel = false;

        if (suggestions == null) {
            suggestions = new Suggestions();
            suggestions.setGuildId(Long.parseLong(guildId));
            requireChannel = true;
        }

        if (channelId != null) {
            if (guildContainer.getChannelById(channelId) == null)
                throw new IllegalAccessException("Channel not found");

            suggestions.setChannelId(Long.parseLong(channelId));
        } else if (requireChannel) {
            throw new IllegalAccessException("Channel not found");
        }

        SQLSession.getSqlConnector().getSqlWorker().updateEntity(suggestions);
    }

    public void removeSuggestion(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);

        Tickets tickets = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Tickets(),
                "SELECT * FROM Tickets WHERE GUILDID=:gid", Map.of("gid", guildId));

        if (tickets != null) {
            guildContainer.getGuild().retrieveWebhooks().queue(c -> c.stream().filter(entry -> entry.getToken() != null)
                    .filter(entry -> entry.getIdLong() == tickets.getLogChannelId() && entry.getToken().equalsIgnoreCase(tickets.getLogChannelWebhookToken()))
                    .forEach(entry -> entry.delete().queue()));

            SQLSession.getSqlConnector().getSqlWorker().deleteEntity(tickets);
        }
    }

    //endregion

    //region Warnings

    public List<WarningContainer> getWarnings(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);

        return SQLSession.getSqlConnector().getSqlWorker().getEntityList(new Warning(),
                "SELECT * FROM Warning WHERE guildId = :gid",
                Map.of("gid", guildId)).stream().map(WarningContainer::new).toList();
    }

    public WarningContainer addWarnings(String sessionIdentifier, String guildId, String userId, String warnings) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);

        Warning warning = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Warning(),
                "SELECT * FROM Warning WHERE guildId = :gid AND userId = :uid",
                Map.of("gid", guildId, "uid", userId));

        if (warning == null) {
            warning = new Warning();
            warning.setGuildId(Long.parseLong(guildId));
            warning.setWarnings(0);
        }

        int additionWarnings = 1;

        try {
            additionWarnings = Integer.parseInt(warnings);
        } catch (NumberFormatException ignored) {
        }

        warning.setWarnings(warning.getWarnings() + additionWarnings);

        warning = SQLSession.getSqlConnector().getSqlWorker().updateEntity(warning);

        return new WarningContainer(warning);
    }

    public WarningContainer removeWarnings(String sessionIdentifier, String guildId, String userId, String warnings) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);

        Warning warning = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Warning(),
                "SELECT * FROM Warning WHERE guildId = :gid AND userId = :uid",
                Map.of("gid", guildId, "uid", userId));

        if (warning == null) {
            warning = new Warning();
            warning.setGuildId(Long.parseLong(guildId));
            warning.setWarnings(0);
            warning.setUserId(Long.parseLong(userId));
        }

        int additionWarnings = 1;

        try {
            additionWarnings = Integer.parseInt(warnings);
        } catch (NumberFormatException ignored) {
        }

        warning.setWarnings(warning.getWarnings() - additionWarnings);

        if (warning.getWarnings() < 0)
            warning.setWarnings(0);

        warning = SQLSession.getSqlConnector().getSqlWorker().updateEntity(warning);

        return new WarningContainer(warning);
    }

    public void clearWarnings(String sessionIdentifier, String guildId, String userId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);

        Warning warning = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Warning(),
                "SELECT * FROM Warning WHERE guildId = :gid AND userId = :uid",
                Map.of("gid", guildId, "uid", userId));

        if (warning == null) {
            warning = new Warning();
            warning.setGuildId(Long.parseLong(guildId));
            warning.setUserId(Long.parseLong(userId));
        }

        warning.setWarnings(0);

        SQLSession.getSqlConnector().getSqlWorker().updateEntity(warning);
    }

    //region Punishments

    public List<PunishmentContainer> getPunishments(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);

        return SQLSession.getSqlConnector().getSqlWorker().getEntityList(new Punishments(),
                "SELECT * FROM Punishments WHERE guildId = :gid",
                Map.of("gid", guildId)).stream().map(c -> new PunishmentContainer(c, guildContainer)).toList();
    }

    public void clearPunishments(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);

        SQLSession.getSqlConnector().getSqlWorker().getEntityList(new Punishments(),
                "SELECT * FROM Punishments WHERE guildId = :gid",
                Map.of("gid", guildId)).forEach(c -> SQLSession.getSqlConnector().getSqlWorker().deleteEntity(c));
    }

    public void removePunishments(String sessionIdentifier, String guildId, String punishmentId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);

        Punishments punishments = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Punishments(),
                "SELECT * FROM Punishments WHERE guildId = :gid AND id = :id",
                Map.of("gid", guildId, "id", punishmentId));

        if (punishments == null)
            throw new IllegalAccessException("Punishment not found");

        if (punishments.getGuildId() != guildContainer.getGuild().getIdLong())
            throw new IllegalAccessException("Punishment not found");

        SQLSession.getSqlConnector().getSqlWorker().deleteEntity(punishments);
    }

    public PunishmentContainer addPunishments(String sessionIdentifier, String guildId, String neededWarnings, String action, String timeoutTime, String roleId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, true);

        if (guildContainer.getGuild().getRoleById(roleId) == null)
            throw new IllegalAccessException("Role not found");

        Punishments punishments = new Punishments();
        punishments.setGuildId(Long.parseLong(guildId));

        try {
            int warnings = Integer.parseInt(neededWarnings);
            if (warnings < 0)
                throw new IllegalAccessException("Invalid warnings");

            int actionInt = Integer.parseInt(action);

            if (actionInt < 0 || actionInt > 5)
                throw new IllegalAccessException("Invalid action");

            long timeout = Long.parseLong(timeoutTime);
            long role = Long.parseLong(roleId);

            punishments.setWarnings(warnings);
            punishments.setAction(actionInt);
            punishments.setTimeoutTime(timeout);
            punishments.setRoleId(role);
        } catch (NumberFormatException e) {
            throw new IllegalAccessException("Invalid number format");
        }

        return  new PunishmentContainer(SQLSession.getSqlConnector().getSqlWorker().updateEntity(punishments));
    }

    //endregion

    //endregion

    //region Custom Command

    public List<CustomCommandContainer> getCustomCommand(String sessionIdentifier, String guildId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true, false);

        return SQLSession.getSqlConnector().getSqlWorker().getEntityList(new CustomCommand(),
                "SELECT * FROM CustomCommand WHERE guild = :gid",
                Map.of("gid", guildId)).stream().map(command -> new CustomCommandContainer(command, guildContainer)).toList();
    }

    public void removeCustomCommand(String sessionIdentifier, String guildId, String commandId) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, false, false);

        CustomCommand command = SQLSession.getSqlConnector().getSqlWorker().getEntity(new CustomCommand(),
                "SELECT * FROM CustomCommand WHERE guild = :gid AND id = :id",
                Map.of("gid", guildId, "id", commandId));

        if (command == null)
            throw new IllegalAccessException("Command not found");

        SQLSession.getSqlConnector().getSqlWorker().deleteEntity(command);
    }

    public CustomCommandContainer addCustomCommand(String sessionIdentifier, String guildId, String commandName, String channelId, String response, String embedJson) throws IllegalAccessException {
        GuildContainer guildContainer = sessionService.retrieveGuild(sessionIdentifier, guildId, true, false);

        CustomCommand command = SQLSession.getSqlConnector().getSqlWorker().getEntity(new CustomCommand(),
                "SELECT * FROM CustomCommand WHERE guild = :gid AND command = :name",
                Map.of("gid", guildId, "name", commandName));

        if (command == null) {
            command = new CustomCommand();
            command.setGuildId(Long.parseLong(guildId));
            command.setName(commandName);
        }

        if (response != null) {
            command.setMessageResponse(response);
        }

        if (embedJson != null) {
            command.setEmbedResponse(JsonParser.parseString(embedJson));
        }

        try {
            long channelIdNumber = Long.parseLong(channelId);
            command.setChannelId(channelIdNumber);
        } catch (NumberFormatException e) {
            throw new IllegalAccessException("Invalid channel id");
        }

        return new CustomCommandContainer(SQLSession.getSqlConnector().getSqlWorker().updateEntity(command), guildContainer);
    }


    //endregion
}
