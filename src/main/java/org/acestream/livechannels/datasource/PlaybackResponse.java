package org.acestream.livechannels.datasource;

import java.io.Serializable;

public class PlaybackResponse implements Serializable {
    public String stat_url;
    public String playback_session_id;
    public String command_url;
    public String playback_url;
    public String event_url;
    public boolean is_direct;
    public int is_live = -1;
    public int is_encrypted = -1;
    public String infohash;
}
