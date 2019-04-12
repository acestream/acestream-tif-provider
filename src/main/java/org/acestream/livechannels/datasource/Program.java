package org.acestream.livechannels.datasource;

import java.io.Serializable;

/**
 * Created by barbarian on 13.06.16.
 */
public class Program implements Serializable {
    public String title;
    public long start;
    public long stop;
    public String description;
    public String poster;
    public String age_restriction;
    public String[] category_names;
    public int[] category_ids;
}
