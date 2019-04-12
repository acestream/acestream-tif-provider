package org.acestream.livechannels.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A convenience class to create and insert channel entries into the database.
 */
public final class Channel implements Serializable {
    public static final String TAG = "Model/Channel";
    public static final long INVALID_CHANNEL_ID = -1;

    private long mId;
    private String mDisplayNumber;
    private String mDisplayName;
    private String mDescription;
    private String mVideoFormat;
    private int mOriginalNetworkId;
    private int mTransportStreamId=1;
    private int mServiceId=0;
    private String mAppLinkText;
    private int mAppLinkColor;
    private String mAppLinkIconUri;
    private String mAppLinkPosterArtUri;
    private String mAppLinkIntentUri;

    private String mPlaybackUrl="";
    private String mInfohash="";
    private String mContentId="";
    private String mTransportFileUrl="";
    private String mUID="";
    private String mLogo="";
    private String mEPGId = "";
    private String[] mCategoryNames = null;
    private int[] mCategoryIds = null;

    private Channel() {
        mId = INVALID_CHANNEL_ID;
    }

    public long getId() {
        return mId;
    }

    public String[] getCategoryNames() {
        return mCategoryNames;
    }

    public void setCategoryNames(String[] categoryNames) {
        mCategoryNames = categoryNames;
    }

    public int[] getCategoryIds() {
        return mCategoryIds;
    }

    public void setCategoryIds(int[] categoryIds) {
        mCategoryIds = categoryIds;
    }

    public String getDisplayNumber() {
        return mDisplayNumber;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getVideoFormat() {
        return mVideoFormat;
    }

    public int getOriginalNetworkId() {
        return mOriginalNetworkId;
    }

    public int getTransportStreamId() {
        return mTransportStreamId;
    }

    public int getServiceId() {
        return mServiceId;
    }

    public String getAppLinkText() {
        return mAppLinkText;
    }

    public int getAppLinkColor() {
        return mAppLinkColor;
    }

    public String getAppLinkIconUri() {
        return mAppLinkIconUri;
    }

    public String getAppLinkPosterArtUri() {
        return mAppLinkPosterArtUri;
    }

    public String getAppLinkIntentUri() {
        return mAppLinkIntentUri;
    }

    public void setAppLinkIntentUri(String intentUri) {
        mAppLinkIntentUri = intentUri;
    }

    @Override
    public String toString() {
        return "Channel{"
                + "id=" + mId
                + ", displayNumber=" + mDisplayNumber
                + ", displayName=" + mDisplayName
                + ", description=" + mDescription
                + ", videoFormat=" + mVideoFormat
                + ", appLinkText=" + mAppLinkText + "}";
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        if (!TextUtils.isEmpty(mDisplayNumber)) {
            values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, mDisplayNumber);
        } else {
            values.putNull(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
        }
        if (!TextUtils.isEmpty(mDisplayName)) {
            values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, mDisplayName);
        } else {
            values.putNull(TvContract.Channels.COLUMN_DISPLAY_NAME);
        }
        if (!TextUtils.isEmpty(mDescription)) {
            values.put(TvContract.Channels.COLUMN_DESCRIPTION, mDescription);
        } else {
            values.putNull(TvContract.Channels.COLUMN_DESCRIPTION);
        }
        if (!TextUtils.isEmpty(mVideoFormat)) {
            values.put(TvContract.Channels.COLUMN_VIDEO_FORMAT, mVideoFormat);
        }
//        else {
//            values.putNull(TvContract.Channels.COLUMN_VIDEO_FORMAT);
//        }
        values.put(TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID, mOriginalNetworkId);
        values.put(TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID, mTransportStreamId);
        values.put(TvContract.Channels.COLUMN_SERVICE_ID, mServiceId);

        String categoryIdsString = "";
        if(mCategoryIds != null) {
            List<String> _list = new ArrayList<>();
            for(int categoryID: mCategoryIds) {
                _list.add(String.valueOf(categoryID));
            }
            categoryIdsString = TextUtils.join("|", _list);
        }

        values.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA, String.format("%s,%s,%s,%s,%s,%s,%s,%s",mUID,mPlaybackUrl,mInfohash,mContentId,mTransportFileUrl,mLogo,mEPGId,categoryIdsString));

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            values.put(TvContract.Channels.COLUMN_APP_LINK_COLOR, mAppLinkColor);
            if (!TextUtils.isEmpty(mAppLinkText)) {
                values.put(TvContract.Channels.COLUMN_APP_LINK_TEXT, mAppLinkText);
            } else {
                values.putNull(TvContract.Channels.COLUMN_APP_LINK_TEXT);
            }
            if (!TextUtils.isEmpty(mAppLinkIconUri)) {
                values.put(TvContract.Channels.COLUMN_APP_LINK_ICON_URI, mAppLinkIconUri);
            } else {
                values.putNull(TvContract.Channels.COLUMN_APP_LINK_ICON_URI);
            }
            if (!TextUtils.isEmpty(mAppLinkPosterArtUri)) {
                values.put(TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI, mAppLinkPosterArtUri);
            } else {
                values.putNull(TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI);
            }
            if (!TextUtils.isEmpty(mAppLinkIntentUri)) {
                values.put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, mAppLinkIntentUri);
            } else {
                values.putNull(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI);
            }
        }
        return values;
    }

    private void copyFrom(Channel other) {
        if (this == other) {
            return;
        }
        mId = other.mId;
        mDisplayNumber = other.mDisplayNumber;
        mDisplayName = other.mDisplayName;
        mDescription = other.mDescription;
        mVideoFormat = other.mVideoFormat;
        mOriginalNetworkId = other.mOriginalNetworkId;
        mTransportStreamId = other.mTransportStreamId;
        mServiceId = other.mServiceId;
        mAppLinkText = other.mAppLinkText;
        mAppLinkColor = other.mAppLinkColor;
        mAppLinkIconUri = other.mAppLinkIconUri;
        mAppLinkPosterArtUri = other.mAppLinkPosterArtUri;
        mAppLinkIntentUri = other.mAppLinkIntentUri;
        mPlaybackUrl = other.mPlaybackUrl;
        mInfohash = other.mInfohash;
        mContentId = other.mContentId;
        mTransportFileUrl = other.mTransportFileUrl;
        mUID = other.mUID;
        mLogo = other.mLogo;
        mEPGId = other.mEPGId;
        mCategoryIds = other.mCategoryIds;
        mCategoryNames = other.mCategoryNames;
    }

    public static Channel fromCursor(Cursor cursor) {
        long channelId = 0;
        Builder builder = new Builder();
        int index = cursor.getColumnIndex(TvContract.Channels._ID);
        if (index >= 0 && !cursor.isNull(index)) {
            channelId = cursor.getLong(index);
            builder.setId(channelId);
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setDisplayNumber(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setDisplayName(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DESCRIPTION);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setDescription(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_VIDEO_FORMAT);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setVideoFormat(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setOriginalNetworkId(cursor.getInt(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setTransportStreamId(cursor.getInt(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_SERVICE_ID);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setServiceId(cursor.getInt(index));
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA);
        if (index >= 0 && !cursor.isNull(index)) {
            String intData = cursor.getString(index);
            if(intData!=null){
                String[] values = intData.split(",", 8);
                if(values.length>0){
                    builder.setUID(values[0]);
                }
                if(values.length>1 && !values[1].equals("null")){
                    builder.setPlaybackUrl(values[1]);
                }
                if(values.length>2 && !values[2].equals("null")){
                    builder.setInfohash(values[2]);
                }
                if(values.length>3 && !values[3].equals("null")){
                    builder.setContentId(values[3]);
                }
                if(values.length>4 && !values[4].equals("null")){
                    builder.setTransportFileUrl(values[4]);
                }
                if(values.length>5){
                    builder.setLogoUrl(values[5]);
                }
                if(values.length>6){
                    builder.setEPGId(values[6]);
                }
                if(values.length>7) {
                    try {
                        String categoryIdsString = values[7];
                        if(categoryIdsString.length() > 0) {
                            String[] _list = categoryIdsString.split("\\|");
                            int[] _ids = new int[_list.length];
                            for (int i = 0; i < _list.length; i++) {
                                _ids[i] = Integer.parseInt(_list[i]);
                            }

                            builder.setCategoryIds(_ids);
                        }
                    }
                    catch(Throwable e) {
                        Log.e(TAG, "failed to parse category ids from db: values=" + values[7], e);
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_TEXT);
            if (index >= 0 && !cursor.isNull(index)) {
                builder.setAppLinkText(cursor.getString(index));
            }
            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_COLOR);
            if (index >= 0 && !cursor.isNull(index)) {
                builder.setAppLinkColor(cursor.getInt(index));
            }
            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_ICON_URI);
            if (index >= 0 && !cursor.isNull(index)) {
                builder.setAppLinkIconUri(cursor.getString(index));
            }
            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI);
            if (index >= 0 && !cursor.isNull(index)) {
                builder.setAppLinkPosterArtUri(cursor.getString(index));
            }
            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI);
            if (index >= 0 && !cursor.isNull(index)) {
                builder.setAppLinkIntentUri(cursor.getString(index));
            }
        }
        return builder.build();
    }

    public String getPlaybackUrl() {
        return mPlaybackUrl;
    }

    public void setPlaybackUrl(String url) {
        this.mPlaybackUrl = url;
    }

    public String getInfohash() {
        return mInfohash;
    }

    public void setInfohash(String infohash) {
        this.mInfohash = infohash;
    }

    public String getContentId() {
        return mContentId;
    }

    public void setContentId(String contentId) {
        this.mContentId = contentId;
    }

    public String getTransportFileUrl() {
        return mTransportFileUrl;
    }

    public void setTransportFileUrl(String url) {
        this.mTransportFileUrl = url;
    }

    public String getUID() {
        return mUID;
    }

    public void setEPGId(String epgId) {
        this.mEPGId = epgId;
    }
    public String getEPGId() {
        return mEPGId;
    }

    public void setUID(String UID) {
        this.mUID = UID;
    }

    public String getLogo() {
        return mLogo;
    }

    public void setLogo(String logo) {
        this.mLogo = logo;
    }

    public static final class Builder {
        private final Channel mChannel;

        public Builder() {
            mChannel = new Channel();
        }

        public Builder(Channel other) {
            mChannel = new Channel();
            mChannel.copyFrom(other);
        }

        public Builder setId(long id) {
            mChannel.mId = id;
            return this;
        }

        public Builder setCategoryNames(String[] categoryNames) {
            mChannel.mCategoryNames= categoryNames;
            return this;
        }

        public Builder setCategoryIds(int[] categoryIds) {
            mChannel.mCategoryIds = categoryIds;
            return this;
        }

        public Builder setDisplayNumber(String displayNumber) {
            mChannel.mDisplayNumber = displayNumber;
            return this;
        }

        public Builder setDisplayName(String displayName) {
            mChannel.mDisplayName = displayName;
            return this;
        }

        public Builder setDescription(String description) {
            mChannel.mDescription = description;
            return this;
        }

        public Builder setVideoFormat(String videoFormat) {
            mChannel.mVideoFormat = videoFormat;
            return this;
        }

        public Builder setOriginalNetworkId(int originalNetworkId) {
            mChannel.mOriginalNetworkId = originalNetworkId;
            return this;
        }

        public Builder setTransportStreamId(int transportStreamId) {
            mChannel.mTransportStreamId = transportStreamId;
            return this;
        }

        public Builder setServiceId(int serviceId) {
            mChannel.mServiceId = serviceId;
            return this;
        }

        public Builder setAppLinkText(String appLinkText) {
            mChannel.mAppLinkText = appLinkText;
            return this;
        }

        public Builder setAppLinkColor(int appLinkColor) {
            mChannel.mAppLinkColor = appLinkColor;
            return this;
        }

        public Builder setAppLinkIconUri(String appLinkIconUri) {
            mChannel.mAppLinkIconUri = appLinkIconUri;
            return this;
        }

        public Builder setAppLinkPosterArtUri(String appLinkPosterArtUri) {
            mChannel.mAppLinkPosterArtUri = appLinkPosterArtUri;
            return this;
        }

        public Builder setAppLinkIntentUri(String appLinkIntentUri) {
            mChannel.mAppLinkIntentUri = appLinkIntentUri;
            return this;
        }

        public Builder setPlaybackUrl(String value) {
            mChannel.mPlaybackUrl = value;
            return this;
        }

        public Builder setInfohash(String value) {
            mChannel.mInfohash = value;
            return this;
        }

        public Builder setContentId(String value) {
            mChannel.mContentId = value;
            return this;
        }

        public Builder setTransportFileUrl(String value) {
            mChannel.mTransportFileUrl = value;
            return this;
        }

        public Builder setUID(String uid) {
            mChannel.mUID = uid;
            return this;
        }

        public Builder setEPGId(String id) {
            mChannel.mEPGId = id;
            return this;
        }

        public Builder setLogoUrl(String logo) {
            mChannel.mLogo = logo;
            return this;
        }

        public Channel build() {
            Channel channel = new Channel();
            channel.copyFrom(mChannel);
            return channel;
        }
    }
}
