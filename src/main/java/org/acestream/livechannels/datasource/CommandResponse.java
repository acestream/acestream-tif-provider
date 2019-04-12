package org.acestream.livechannels.datasource;

import java.io.Serializable;

public class CommandResponse implements Serializable {
    public String status;
    public String playback_session_id;
    public int progress;
    public int uploaded;
    public int downloaded;
    public int speed_down;
    public int speed_up;
    public int peers;
    public int total_progress;
}
