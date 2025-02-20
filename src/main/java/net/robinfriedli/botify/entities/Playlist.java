package net.robinfriedli.botify.entities;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.audio.PlayableImpl;

@Entity
@Table(name = "playlist")
public class Playlist implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "name")
    private String name;
    @Column(name = "created_user")
    private String createdUser;
    @Column(name = "created_user_id")
    private String createdUserId;
    @Column(name = "guild")
    private String guild;
    @Column(name = "guild_id")
    private String guildId;
    @OneToMany(mappedBy = "playlist")
    private List<Song> songs = Lists.newArrayList();
    @OneToMany(mappedBy = "playlist")
    private List<Video> videos = Lists.newArrayList();
    @OneToMany(mappedBy = "playlist")
    private List<UrlTrack> urlTracks = Lists.newArrayList();

    public Playlist() {
    }

    public Playlist(String name, User user, Guild guild) {
        setName(name);
        createdUser = user.getName();
        createdUserId = user.getId();
        this.guild = guild.getName();
        this.guildId = guild.getId();
    }

    public static String sanatizeName(String name) {
        if (name.contains(" ")) {
            Splitter splitter = Splitter.on(" ").trimResults().omitEmptyStrings();
            return Joiner.on(" ").join(splitter.split(name));
        }

        return name;
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getDuration() {
        return getItems().stream().mapToLong(PlaylistItem::getDuration).sum();
    }

    public int getSize() {
        return songs.size() + videos.size() + urlTracks.size();
    }

    public String getCreatedUser() {
        return createdUser;
    }

    public void setCreatedUser(String createdUser) {
        this.createdUser = createdUser;
    }

    public String getCreatedUserId() {
        return createdUserId;
    }

    public void setCreatedUserId(String createdUserId) {
        this.createdUserId = createdUserId;
    }

    public String getGuild() {
        return guild;
    }

    public void setGuild(String guild) {
        this.guild = guild;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public List<PlaylistItem> getItems() {
        return Stream.of(getSongs(), getVideos(), getUrlTracks())
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    public List<PlaylistItem> getItemsSorted() {
        return getItemsSorted(false);
    }

    public List<PlaylistItem> getItemsSorted(boolean ignoreNullIndex) {
        return Stream.of(getSongs(), getVideos(), getUrlTracks())
            .flatMap(Collection::stream)
            .filter(item -> {
                if (ignoreNullIndex) {
                    return item.getIndex() != null;
                } else {
                    return true;
                }
            })
            .sorted(Comparator.comparing(PlaylistItem::getIndex))
            .collect(Collectors.toList());
    }

    public Stream<PlaylistItem> stream() {
        return Stream.of(getSongs(), getVideos(), getUrlTracks()).flatMap(Collection::stream);
    }

    /**
     * Returns the items in this playlist as objects supported by the {@link PlayableImpl} class. Note that getting the
     * Spotify track for a Song requires this method to be invoked with client credentials
     */
    public List<Object> getTracks(SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException {
        List<PlaylistItem> playlistItems = getItemsSorted();

        Map<Object, Integer> itemsWithIndex = new HashMap<>();
        Map<String, Integer> trackIdsWithIndex = new HashMap<>();
        for (int i = 0; i < playlistItems.size(); i++) {
            PlaylistItem item = playlistItems.get(i);
            if (item instanceof Song) {
                trackIdsWithIndex.put(((Song) item).getId(), i);
            } else if (item instanceof Video) {
                itemsWithIndex.put(((Video) item).asYouTubeVideo(), i);
            } else if (item instanceof UrlTrack) {
                itemsWithIndex.put(item, i);
            }
        }

        List<List<String>> batches = Lists.partition(Lists.newArrayList(trackIdsWithIndex.keySet()), 50);
        for (List<String> batch : batches) {
            Track[] tracks = spotifyApi.getSeveralTracks(batch.toArray(new String[0])).build().execute();
            for (Track track : tracks) {
                itemsWithIndex.put(track, trackIdsWithIndex.get(track.getId()));
            }
        }

        return itemsWithIndex.keySet().stream().sorted(Comparator.comparing(itemsWithIndex::get)).collect(Collectors.toList());
    }

    /**
     * returns all Songs as Spotify tracks including all videos that are redirected Spotify tracks i.e. the attribute
     * redirectedSpotifyId is set. Mind that this method has to be invoked with client credentials
     */
    public List<Track> asTrackList(SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException {
        List<Track> tracks = Lists.newArrayList();
        for (PlaylistItem item : getItemsSorted()) {
            if (item instanceof Song) {
                tracks.add(spotifyApi.getTrack(((Song) item).getId()).build().execute());
            } else if (item instanceof Video && ((Video) item).getRedirectedSpotifyId() != null) {
                tracks.add(spotifyApi.getTrack(((Video) item).getRedirectedSpotifyId()).build().execute());
            }
        }

        return tracks;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    public List<Video> getVideos() {
        return videos;
    }

    public void setVideos(List<Video> videos) {
        this.videos = videos;
    }

    public List<UrlTrack> getUrlTracks() {
        return urlTracks;
    }

    public void setUrlTracks(List<UrlTrack> urlTracks) {
        this.urlTracks = urlTracks;
    }

    public boolean isEmpty() {
        return songs.isEmpty() && videos.isEmpty() && urlTracks.isEmpty();
    }

}
