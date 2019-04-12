package org.acestream.livechannels.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.livechannels.datasource.DataSourceProvider;

/**
 * Created by barbarian on 08.06.16.
 */
public class LiveChannelsUtils {
    private static String TAG = "LiveUtils";
    private static String ANDROID_TV_LIVE_CHANNELS = "com.google.android.tv";
    private static String SONY_LIVE_CHANNELS = "com.sony.dtv.tvplayer";
    public static Intent getLiveChannels(Activity mActivity) {
        if(isPackageInstalled(ANDROID_TV_LIVE_CHANNELS, mActivity)) {
            Intent i = mActivity.getPackageManager().getLaunchIntentForPackage(ANDROID_TV_LIVE_CHANNELS);
            return i;
        } else if(isPackageInstalled(SONY_LIVE_CHANNELS, mActivity)) {
            Intent i = mActivity.getPackageManager().getLaunchIntentForPackage(SONY_LIVE_CHANNELS);
            return i;
        }
        return null;
    }
    private static boolean isPackageInstalled(String packagename, Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
    /**
     Returns the DataSourceProvider that was defined by the project's manifest
     **/
    public static DataSourceProvider getTvDataSourceProvider(Context mContext){
        ApplicationInfo app = null;
        try {
            Log.d(TAG, mContext.getPackageName()+" >");
            app = mContext.getPackageManager().getApplicationInfo(mContext.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = app.metaData;
            final String service = bundle.getString("TvDataSourceProvider");
            if(!TextUtils.isEmpty(service)) {
                return  (DataSourceProvider) Class.forName(service).getConstructors()[0].newInstance();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
