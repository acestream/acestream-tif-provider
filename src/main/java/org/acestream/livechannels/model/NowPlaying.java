package org.acestream.livechannels.model;

import android.net.Uri;

import com.google.android.exoplayer.util.Util;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by barbarian on 04.06.16.
 */
public class NowPlaying implements Serializable {
    private Uri channelUri;
    private ContentDescriptor contentDescriptor = null;
    private String statusUrl = null;
    private String streamUrl = null;
    private String eventUrl = null;
    private String commandUrl = null;
    private Date startedAt = null;
    public boolean playbackStarted = false;
    private String mOutputFormat = "http";
    private Channel channel;
    private Program program;
    private boolean mIsDirect = false;
    private String mInfohash = null;
    private String mUserAgent = null;

    public NowPlaying(Uri channelUri){
        this.channelUri = channelUri;
    }

    public Uri getChannelUri() {
        return channelUri;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public Program getProgram() {
        return program;
    }

    public void setProgram(Program program) {
        this.program = program;
    }

    public ContentDescriptor getContentDescriptor() {
        return contentDescriptor;
    }

    public void setContentDescriptor(ContentDescriptor contentDescriptor) {
        this.contentDescriptor = contentDescriptor;
    }

    public String getStatusUrl() {
        return statusUrl;
    }

    public void setStatusUrl(String statusUrl) {
        this.statusUrl = statusUrl;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    public String getOutputFormat() {
        return mOutputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        mOutputFormat = outputFormat;
    }

    public void setEventUrl(String eventUrl) {
        this.eventUrl = eventUrl;
    }

    public String getEventUrl() {
        return eventUrl;
    }

    public void setCommandUrl(String commandUrl) {
        this.commandUrl = commandUrl;
    }

    public String getCommandUrl(String method) {
        String url = commandUrl + "?method=" + method;
        return url;
    }

    public void setStartedAt(Date startedAt) {
        this.startedAt = startedAt;
    }

    public Date getStartedAt() {
        return startedAt;
    }

    public boolean isStarted() {
        return (startedAt != null);
    }

    public void setIsDirect(boolean value) {
        mIsDirect = value;
    }

    public boolean isDirect() {
        return mIsDirect;
    }

    public String getInfohash() {
        return mInfohash;
    }

    public void setInfohash(String infohash) {
        mInfohash = infohash;
    }

    public String getUserAgent() {
        return mUserAgent;
    }

    public void setUserAgent(String userAgent) {
        mUserAgent = userAgent;
    }

    @Override
    public String toString(){
        return String.format(
                "<Session: descriptor=%s uri=%s>",
                contentDescriptor,
                channelUri
        );
    }
}
