package net.robinfriedli.botify.entities;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.FlushModeType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.stringlist.StringListImpl;
import org.hibernate.Session;
import org.hibernate.query.Query;

@Entity
@Table(name = "song")
public class Song extends PlaylistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "id")
    private String id;
    @Column(name = "name")
    private String name;
    @ManyToMany(fetch = FetchType.EAGER)
    private List<Artist> artists = Lists.newArrayList();

    public Song() {
    }

    public Song(Track track, User user, Playlist playlist, Session session) {
        super(user, playlist);
        id = track.getId();
        name = track.getName();
        for (ArtistSimplified artist : track.getArtists()) {
            Query<Artist> query = session
                .createQuery(" from " + Artist.class.getName() + " where id = '" + artist.getId() + "'", Artist.class);
            query.setFlushMode(FlushModeType.AUTO);
            Optional<Artist> optionalArtist = query.uniqueResultOptional();
            if (optionalArtist.isPresent()) {
                artists.add(optionalArtist.get());
            } else {
                Artist newArtist = new Artist(artist.getId(), artist.getName());
                session.persist(newArtist);
                artists.add(newArtist);
            }
        }
        this.duration = track.getDurationMs();
    }

    @Override
    public PlaylistItem copy(Playlist playlist) {
        Song song = new Song();
        song.setId(getId());
        song.setName(getName());
        song.setArtists(Lists.newArrayList(getArtists()));
        song.setDuration(getDuration());
        song.setAddedUser(getAddedUser());
        song.setAddedUserId(getAddedUserId());
        song.setPlaylist(playlist);
        return song;
    }

    @Override
    public boolean matches(String searchTerm) {
        return name.equalsIgnoreCase(searchTerm);
    }

    @Override
    public String display() {
        String artistString = StringListImpl.create(artists, Artist::getName).toSeparatedString(", ");
        return name + " by " + artistString;
    }

    @Override
    public void add() {
        getPlaylist().getSongs().add(this);
    }

    public Track asTrack(SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException {
        return spotifyApi.getTrack(getId()).build().execute();
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Artist> getArtists() {
        return artists;
    }

    public void setArtists(List<Artist> artists) {
        this.artists = artists;
    }

}
