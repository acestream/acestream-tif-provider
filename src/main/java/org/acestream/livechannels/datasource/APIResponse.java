package org.acestream.livechannels.datasource;

import java.io.Serializable;

/**
 * Created by barbarian on 13.06.16.
 */
public class APIResponse<T> implements Serializable {
    public T result;
    public String error;
}
