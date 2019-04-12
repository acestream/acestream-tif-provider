package org.acestream.livechannels.utils;

import android.media.tv.TvTrackInfo;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.exoplayer.MediaFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Created by barbarian on 25.07.16.
 */
public class TracksManager {
    private final TvTrackInfo[][] mTvTracks;
    public static final int[] trackTypes = {
            TvTrackInfo.TYPE_AUDIO,
            TvTrackInfo.TYPE_VIDEO
    };

    public TracksManager() {
        this.mTvTracks = new TvTrackInfo[trackTypes.length][];
        for (int i = 0; i < this.mTvTracks.length; i++) {
            this.mTvTracks[i] = new TvTrackInfo[0];
        }
    }

    public void updateExoPlayerTracks(@NonNull SparseArray<MediaFormat[]> playerTracks) {
        for (int tvTrackType : trackTypes) {
            final MediaFormat[] mediaFormats = playerTracks.get(tvTrackType, new MediaFormat[0]);

            if (this.mTvTracks[tvTrackType].length != mediaFormats.length) {
                this.mTvTracks[tvTrackType] = new TvTrackInfo[mediaFormats.length];
            }
            for (int j = 0; j < mediaFormats.length; j++) {
                this.mTvTracks[tvTrackType][j] = createTvTrackInfo(tvTrackType,j, mediaFormats[j]);
            }
        }
    }


    private TvTrackInfo createTvTrackInfo(int tvTrackType,int trackIndex, @NonNull MediaFormat fmt) {
        TvTrackInfo.Builder builder = new TvTrackInfo.Builder(tvTrackType, generateTrackId(tvTrackType, trackIndex));
        if (fmt.language != null) {
            builder.setLanguage(fmt.language);
        }
        switch (tvTrackType) {
            case TvTrackInfo.TYPE_AUDIO /*0*/:
                if (fmt.channelCount != -1) {
                    builder.setAudioChannelCount(fmt.channelCount);
                }
                if (fmt.sampleRate != -1) {
                    builder.setAudioSampleRate(fmt.sampleRate);
                    break;
                }
                break;
            case TvTrackInfo.TYPE_VIDEO /*1*/:
                if (!(fmt.width == -1 || fmt.height == -1)) {
                    VideoPlaybackSize vps = new VideoPlaybackSize(fmt);
                    builder.setVideoWidth(vps.width).setVideoHeight(vps.height);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && fmt.pixelWidthHeightRatio != -1) {
                        builder.setVideoPixelAspectRatio(vps.pixelAspectRatio);
                        break;
                    }
                }
            default:
                break;
        }
        return builder.build();
    }

    public static String generateTrackId(int tvTrackType, int idx) {
        return String.valueOf(tvTrackType) + "-" + idx;
    }

    public boolean hasTracks(int tvTrackType) {
        return tvTrackType < this.mTvTracks.length && this.mTvTracks[tvTrackType].length > 0;
    }

    public TvTrackInfo[] getTracks(int tvTrackType) {
        return this.mTvTracks[tvTrackType];
    }

    public List<TvTrackInfo> getAllTracks() {
        TvTrackInfo[] audioTracks = this.mTvTracks[0];
        TvTrackInfo[] videoTracks = this.mTvTracks[1];
        ArrayList<TvTrackInfo> allTracks = new ArrayList((audioTracks.length + videoTracks.length));
        Collections.addAll(allTracks, audioTracks);
        Collections.addAll(allTracks, videoTracks);
        return allTracks;
    }

    public int getTrackIndex(int tvTrackType, @NonNull String trackId) {
        for (int i = 0; i < this.mTvTracks[tvTrackType].length; i++) {
            if (this.mTvTracks[tvTrackType][i].getId().equals(trackId)) {
                return i;
            }
        }
        return -1;
    }

    public String getTrackId(int tvTrackType, int index) {
        if (index < 0 || index >= this.mTvTracks[tvTrackType].length) {
           return "";
        }
        return this.mTvTracks[tvTrackType][index].getId();
    }

    @Nullable
    public TvTrackInfo updateVideoSize(String trackId,VideoPlaybackSize videoSize) {
        int trackIndex = getTrackIndex(TvTrackInfo.TYPE_VIDEO,trackId);
        if(trackIndex<0) return null;
        if (trackIndex >= this.mTvTracks[TvTrackInfo.TYPE_VIDEO].length) {
            Log.e(TracksManager.class.getSimpleName(),String.format("Wrong video track index: %d. All video tracks: %d", trackIndex, this.mTvTracks[TvTrackInfo.TYPE_VIDEO].length));
            return null;
        }
        TvTrackInfo tviCurrent = this.mTvTracks[TvTrackInfo.TYPE_VIDEO][trackIndex];
        if (tviCurrent == null) {
            Log.w(TracksManager.class.getSimpleName(),"Can't update current track with video size. Wasn't built yet.");
            return null;
        }
        boolean samePixelAspectRatio = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            samePixelAspectRatio = videoSize.pixelAspectRatio == tviCurrent.getVideoPixelAspectRatio();
        }
        if (samePixelAspectRatio && videoSize.width == tviCurrent.getVideoWidth() && videoSize.height == tviCurrent.getVideoHeight()) {
            return null;
        }

        TvTrackInfo.Builder builder = new TvTrackInfo.Builder( TvTrackInfo.TYPE_VIDEO,trackId)
            .setLanguage(tviCurrent.getLanguage())
            .setVideoWidth(videoSize.width)
            .setVideoHeight(videoSize.height)
            .setVideoFrameRate(tviCurrent.getVideoFrameRate());
        if (tviCurrent.getExtra() != null) {
            builder.setExtra(tviCurrent.getExtra());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setVideoPixelAspectRatio(videoSize.pixelAspectRatio).setDescription(tviCurrent.getDescription());
        }
        TvTrackInfo tvi = builder.build();
        this.mTvTracks[TvTrackInfo.TYPE_VIDEO][trackIndex] = tvi;
        return tvi;
    }

    public TvTrackInfo addVideoTrack(VideoPlaybackSize vps) {
        TvTrackInfo.Builder builder = new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, generateTrackId(TvTrackInfo.TYPE_VIDEO, this.mTvTracks[TvTrackInfo.TYPE_VIDEO].length));

        builder.setVideoWidth(vps.width).setVideoHeight(vps.height);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setVideoPixelAspectRatio(vps.pixelAspectRatio);
        }

        TvTrackInfo[] videoTracks = new TvTrackInfo[this.mTvTracks[TvTrackInfo.TYPE_VIDEO].length+1];

        for(int i=0;i<this.mTvTracks[TvTrackInfo.TYPE_VIDEO].length;i++){
            videoTracks[i]=this.mTvTracks[TvTrackInfo.TYPE_VIDEO][i];
        }

        TvTrackInfo info = builder.build();
        videoTracks[videoTracks.length-1]=info;

        this.mTvTracks[TvTrackInfo.TYPE_VIDEO] = videoTracks;

        return info;
    }

    /**
     * Gets the index of the track for a given track id.
     *
     * @param trackId the track id.
     * @return the track index for the given id, as an integer.
     */
    public static int getIndexFromTrackId(String trackId) {
        return Integer.parseInt(trackId.split("-")[1]);
    }

    public void updateTracks(@NonNull SparseArray<TvTrackInfo[]> tvTracks) {
        for (int tvTrackType : trackTypes) {
            this.mTvTracks[tvTrackType] = tvTracks.get(tvTrackType, new TvTrackInfo[0]);
        }
    }
}
