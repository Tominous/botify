package net.robinfriedli.botify.command.commands;

import java.awt.Color;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.EmbedBuilder;
import net.robinfriedli.botify.audio.YouTubePlaylist;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.audio.YouTubeVideo;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.exceptions.NoSpotifyResultsFoundException;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.botify.util.Table2;
import net.robinfriedli.botify.util.Util;
import net.robinfriedli.stringlist.StringListImpl;
import org.hibernate.Session;

public class SearchCommand extends AbstractCommand {

    public SearchCommand(CommandContribution commandContribution, CommandContext commandContext, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, commandContext, commandManager, commandString, false, identifier, description, Category.SEARCH);
    }

    @Override
    public void doRun() throws Exception {
        if (argumentSet("list")) {
            if (argumentSet("spotify")) {
                listSpotifyList();
            } else if (argumentSet("youtube")) {
                listYouTubePlaylists();
            } else {
                listLocalList();
            }
        } else if (argumentSet("album")) {
            listSpotifyAlbum();
        } else {
            if (argumentSet("youtube")) {
                searchYouTubeVideo();
            } else {
                searchSpotifyTrack();
            }
        }
    }

    private void searchSpotifyTrack() throws Exception {
        if (getCommandBody().isBlank()) {
            throw new InvalidCommandException("No search term entered");
        }

        SpotifyApi spotifyApi = getContext().getSpotifyApi();
        List<Track> found;
        if (argumentSet("own")) {
            found = runWithLogin(getContext().getUser(), () -> SearchEngine.searchOwnTrack(spotifyApi, getCommandBody()));
        } else {
            found = runWithCredentials(() -> SearchEngine.searchTrack(spotifyApi, getCommandBody()));
        }
        if (!found.isEmpty()) {
            EmbedBuilder embedBuilder = new EmbedBuilder();

            Util.appendEmbedList(
                embedBuilder,
                found,
                track -> track.getName() + " - " + track.getAlbum().getName() + " - " +
                    StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", "),
                "Track - Album - Artist"
            );
            embedBuilder.setColor(Color.decode("#1DB954"));

            sendMessage(getContext().getChannel(), embedBuilder.build());
        } else {
            throw new NoSpotifyResultsFoundException("No Spotify track found for " + getCommandBody());
        }
    }

    private void searchYouTubeVideo() throws InterruptedException {
        YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
        if (argumentSet("limit")) {
            int limit = getArgumentValue("limit", Integer.class);
            if (!(limit > 0 && limit <= 10)) {
                throw new InvalidCommandException("Limit must be between 1 and 10");
            }

            List<YouTubeVideo> youTubeVideos = youTubeService.searchSeveralVideos(limit, getCommandBody());
            if (youTubeVideos.size() == 1) {
                listYouTubeVideo(youTubeVideos.get(0));
            } else if (youTubeVideos.isEmpty()) {
                throw new NoResultsFoundException("No YouTube videos found for " + getCommandBody());
            } else {
                askQuestion(youTubeVideos, youTubeVideo -> {
                    try {
                        return youTubeVideo.getTitle();
                    } catch (InterruptedException e) {
                        // Unreachable since only HollowYouTubeVideos might get interrupted
                        throw new RuntimeException(e);
                    }
                });
            }
        } else {
            listYouTubeVideo(youTubeService.searchVideo(getCommandBody()));
        }
    }

    private void listYouTubeVideo(YouTubeVideo youTubeVideo) throws InterruptedException {
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("Title: ").append(youTubeVideo.getTitle()).append(System.lineSeparator());
        responseBuilder.append("Id: ").append(youTubeVideo.getId()).append(System.lineSeparator());
        responseBuilder.append("Link: ").append("https://www.youtube.com/watch?v=").append(youTubeVideo.getId()).append(System.lineSeparator());
        responseBuilder.append("Duration: ").append(Util.normalizeMillis(youTubeVideo.getDuration()));

        sendMessage(getContext().getChannel(), responseBuilder.toString());
    }

    private void listLocalList() throws IOException {
        if (getCommandBody().isBlank()) {
            Session session = getContext().getSession();
            List<Playlist> playlists;
            if (isPartitioned()) {
                playlists = session.createQuery("from " + Playlist.class.getName() + " where guild_id = '" + getContext().getGuild().getId() + "'", Playlist.class).getResultList();
            } else {
                playlists = session.createQuery("from " + Playlist.class.getName(), Playlist.class).getResultList();
            }
            if (playlists.isEmpty()) {
                throw new NoResultsFoundException("No playlists");
            }

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setColor(Color.decode("#1DB954"));

            Table2 table = new Table2(embedBuilder);
            table.addColumn("Playlist", playlists, Playlist::getName);
            table.addColumn("Duration", playlists, playlist -> Util.normalizeMillis(playlist.getDuration()));
            table.addColumn("Items", playlists, playlist -> String.valueOf(playlist.getSize()));
            table.build();

            sendMessage(getContext().getChannel(), embedBuilder.build());
        } else {
            Playlist playlist = SearchEngine.searchLocalList(getContext().getSession(), getCommandBody(), isPartitioned(), getContext().getGuild().getId());
            if (playlist == null) {
                throw new NoResultsFoundException("No local list found for " + getCommandBody());
            }

            String createdUserId = playlist.getCreatedUserId();
            String createdUser;
            if (createdUserId.equals("system")) {
                createdUser = playlist.getCreatedUser();
            } else {
                createdUser = getContext().getJda().getUserById(createdUserId).getName();
            }


            String baseUri = PropertiesLoadingService.requireProperty("BASE_URI");
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.addField("Name", playlist.getName(), true);
            embedBuilder.addField("Duration", Util.normalizeMillis(playlist.getDuration()), true);
            embedBuilder.addField("Created by", createdUser, true);
            embedBuilder.addField("Tracks", String.valueOf(playlist.getSize()), true);
            embedBuilder.addBlankField(false);

            String url = baseUri +
                String.format("/list?name=%s&guildId=%s", URLEncoder.encode(playlist.getName(), StandardCharsets.UTF_8), playlist.getGuildId());
            embedBuilder.addField("First tracks:", "[Full list](" + url + ")", false);

            List<PlaylistItem> items = playlist.getItemsSorted();
            Util.appendEmbedList(
                embedBuilder,
                items.size() > 5 ? items.subList(0, 5) : items,
                item -> item.display() + " - " + Util.normalizeMillis(item.getDuration()),
                "Track - Duration"
            );

            sendWithLogo(getContext().getChannel(), embedBuilder);
        }
    }

    private void listYouTubePlaylists() {
        YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
        if (argumentSet("limit")) {
            int limit = getArgumentValue("limit", Integer.class);
            if (!(limit > 0 && limit <= 10)) {
                throw new InvalidCommandException("Limit must be between 1 and 10");
            }

            List<YouTubePlaylist> playlists = youTubeService.searchSeveralPlaylists(limit, getCommandBody());
            if (playlists.size() == 1) {
                listYouTubePlaylist(playlists.get(0));
            } else if (playlists.isEmpty()) {
                throw new NoResultsFoundException("No YouTube playlist found for " + getCommandBody());
            } else {
                askQuestion(playlists, YouTubePlaylist::getTitle, YouTubePlaylist::getChannelTitle);
            }
        } else {
            listYouTubePlaylist(youTubeService.searchPlaylist(getCommandBody()));
        }
    }

    private void listYouTubePlaylist(YouTubePlaylist youTubePlaylist) {
        if (getCommandBody().isBlank()) {
            throw new InvalidCommandException("Command body may not be empty when searching YouTube list");
        }

        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("Title: ").append(youTubePlaylist.getTitle()).append(System.lineSeparator());
        responseBuilder.append("Url: ").append(youTubePlaylist.getUrl()).append(System.lineSeparator());
        responseBuilder.append("Videos: ").append(youTubePlaylist.getVideos().size()).append(System.lineSeparator());
        responseBuilder.append("Owner: ").append(youTubePlaylist.getChannelTitle());

        sendMessage(getContext().getChannel(), responseBuilder.toString());
    }

    private void listSpotifyList() throws Exception {
        SpotifyApi spotifyApi = getContext().getSpotifyApi();
        String commandBody = getCommandBody();

        if (commandBody.isBlank()) {
            throw new InvalidCommandException("Command may not be empty when searching spotify lists");
        }

        List<PlaylistSimplified> playlists;
        if (argumentSet("own")) {
            playlists = runWithLogin(getContext().getUser(), () -> SearchEngine.searchOwnPlaylist(spotifyApi, getCommandBody()));
        } else {
            playlists = runWithCredentials(() -> SearchEngine.searchSpotifyPlaylist(spotifyApi, getCommandBody()));
        }
        if (playlists.size() == 1) {
            PlaylistSimplified playlist = playlists.get(0);
            List<Track> tracks = runWithCredentials(() -> SearchEngine.getPlaylistTracks(spotifyApi, playlist));
            listTracks(tracks, playlist.getName(), playlist.getOwner().getDisplayName(), null, "playlist/" + playlist.getId());
        } else if (playlists.isEmpty()) {
            throw new NoSpotifyResultsFoundException("No Spotify playlist found for " + getCommandBody());
        } else {
            askQuestion(playlists, PlaylistSimplified::getName, p -> p.getOwner().getDisplayName());
        }
    }

    private void listSpotifyAlbum() throws Exception {
        SpotifyApi spotifyApi = getContext().getSpotifyApi();

        List<AlbumSimplified> albums = runWithCredentials(() -> SearchEngine.searchSpotifyAlbum(spotifyApi, getCommandBody()));
        if (albums.size() == 1) {
            AlbumSimplified album = albums.get(0);
            List<Track> tracks = runWithCredentials(() -> SearchEngine.getAlbumTracks(spotifyApi, album.getId()));
            listTracks(
                tracks,
                album.getName(),
                null,
                StringListImpl.create(album.getArtists(), ArtistSimplified::getName).toSeparatedString(", "),
                "album/" + album.getId()
            );
        } else if (albums.isEmpty()) {
            throw new NoSpotifyResultsFoundException("No album found for " + getCommandBody());
        } else {
            askQuestion(albums, AlbumSimplified::getName, album -> StringListImpl.create(album.getArtists(), ArtistSimplified::getName).toSeparatedString(", "));
        }
    }

    private void listTracks(List<Track> tracks, String name, String owner, String artist, String path) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        long totalDuration = tracks.stream().mapToLong(Track::getDurationMs).sum();

        embedBuilder.addField("Name", name, true);
        embedBuilder.addField("Song count", String.valueOf(tracks.size()), true);
        embedBuilder.addField("Duration", Util.normalizeMillis(totalDuration), true);
        if (owner != null) {
            embedBuilder.addField("Owner", owner, true);
        }
        if (artist != null) {
            embedBuilder.addField("Artist", artist, true);
        }

        if (!tracks.isEmpty()) {
            String url = "https://open.spotify.com/" + path;
            embedBuilder.addField("First tracks:", "[Full list](" + url + ")", false);

            Util.appendEmbedList(
                embedBuilder,
                tracks.size() > 5 ? tracks.subList(0, 5) : tracks,
                track -> track.getName() + " - " +
                    StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ") + " - " +
                    Util.normalizeMillis(track.getDurationMs()),
                "Track - Artist - Duration"
            );
        }

        embedBuilder.setColor(Color.decode("#1DB954"));
        sendMessage(getContext().getChannel(), embedBuilder.build());
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public void withUserResponse(Object chosenOption) throws Exception {
        if (chosenOption instanceof PlaylistSimplified) {
            SpotifyApi spotifyApi = getContext().getSpotifyApi();
            PlaylistSimplified playlist = (PlaylistSimplified) chosenOption;
            List<Track> tracks = runWithCredentials(() -> SearchEngine.getPlaylistTracks(spotifyApi, playlist));
            listTracks(tracks, playlist.getName(), playlist.getOwner().getDisplayName(), null, "playlist/" + playlist.getId());
        } else if (chosenOption instanceof YouTubePlaylist) {
            listYouTubePlaylist((YouTubePlaylist) chosenOption);
        } else if (chosenOption instanceof YouTubeVideo) {
            listYouTubeVideo((YouTubeVideo) chosenOption);
        } else if (chosenOption instanceof AlbumSimplified) {
            SpotifyApi spotifyApi = getContext().getSpotifyApi();
            AlbumSimplified album = (AlbumSimplified) chosenOption;
            List<Track> tracks = runWithCredentials(() -> SearchEngine.getAlbumTracks(spotifyApi, album.getId()));
            listTracks(tracks,
                album.getName(),
                null,
                StringListImpl.create(album.getArtists(), ArtistSimplified::getName).toSeparatedString(", "),
                "album/" + album.getId());
        }
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("spotify").excludesArguments("youtube").setRequiresInput(true)
            .setDescription("Search for Spotify track or playlist. This supports Spotify query syntax (i.e. the filters \"artist:\", \"album:\", etc.). This is the default option when searching for tracks.");
        argumentContribution.map("youtube").excludesArguments("spotify").setRequiresInput(true)
            .setDescription("Search for YouTube video or playlist.");
        argumentContribution.map("list")
            .setDescription("Search for a playlist.");
        argumentContribution.map("local").needsArguments("list")
            .setDescription("Search for a local playlist or list all of them. This is default when searching for lists.");
        argumentContribution.map("own").needsArguments("spotify")
            .setDescription("Limit search to Spotify tracks or playlists in the current user's library. This requires a Spotify login. Spotify search filters (\"artist:\", \"album:\" etc.) are not supported with this argument.");
        argumentContribution.map("limit").needsArguments("youtube").setRequiresValue(true)
            .setDescription("Show a selection of YouTube playlists or videos to chose from. Requires value from 1 to 10: $limit=5");
        argumentContribution.map("album").needsArguments("spotify").excludesArguments("own").setRequiresInput(true)
            .setDescription("Search for a Spotify album. Note that this argument is only required when searching, not when entering a URL.");
        return argumentContribution;
    }

}
