package org.acestream.livechannels.datasource;

import java.io.Serializable;

/**
 * Created by barbarian on 13.06.16.
 */
public class Channel implements Serializable, Comparable<Channel> {
    public long id;
    public String title;
    public String playback_url;
    public String url;
    public String infohash;
    public String content_id;
    public String icon;
    public String[] categories;
    public int[] category_ids;
    public String hash;

    @Override
    public int compareTo(Channel other) {
        if(title == null) {
            return -1;
        }
        else if(other.title == null) {
            return 1;
        }
        else {
            return title.compareToIgnoreCase(other.title);
        }
    }
}
