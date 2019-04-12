package org.acestream.livechannels.datasource;

public class ApiError extends Throwable {
    public ApiError(String msg) {
        super(msg);
    }
    public ApiError(Throwable e) {
        super(e);
    }
    public ApiError(String msg, Throwable e) {
        super(msg, e);
    }
}
