package org.acestream.livechannels.utils;

import com.google.android.exoplayer.MediaFormat;

public final class VideoPlaybackSize {
    private static final boolean ENABLE_LIVE_CHANNELS_API23_ASPECT_RATIO_WORKAROUND = true;
    public int height;
    public float pixelAspectRatio;
    public int width;

    public VideoPlaybackSize(int width, int height, float pixelAspectRatio) {
        if (((double) pixelAspectRatio) == 1.0d || width == 0 || height == 0) {
            this.width = width;
            this.height = height;
            this.pixelAspectRatio = pixelAspectRatio;
            return;
        }
        this.height = height;
        this.width = (int) (((float) width) * pixelAspectRatio);
        this.pixelAspectRatio = 1.0f;
    }

    public VideoPlaybackSize(MediaFormat format) {
        this(format.width != -1?format.width:0, format.height != -1?format.height:0,
                format.pixelWidthHeightRatio != -1 ? format.pixelWidthHeightRatio : 1.0f);
    }

    public boolean equals(Object o) {
        return (o == this || ((o instanceof VideoPlaybackSize) && this.width == ((VideoPlaybackSize) o).width && this.height == ((VideoPlaybackSize) o).height && this.pixelAspectRatio == ((VideoPlaybackSize) o).pixelAspectRatio));
    }
}
