package org.acestream.livechannels.datasource;

import java.io.Serializable;

/*
Need this class because of inconsistent engine responses.
Some HTTP API methods returns {response: {}, error: null} instead of {result: {}, error: null}
 */
public class APIResponse2<T> implements Serializable {
    public T response;
    public String error;
}
