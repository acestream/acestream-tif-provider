package org.acestream.livechannels.utils;

import android.content.ComponentName;
import android.content.Context;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import org.acestream.livechannels.Constants;
import org.acestream.livechannels.tvinput.TvInputService;


/**
 * Created by barbarian on 25.07.16.
 */
public class TVInputUtils {

    public static String getInputId(Context  context, Bundle args){
        if(context==null) return null;

        if(args!=null&&args.containsKey(TvInputInfo.EXTRA_INPUT_ID)){
            return args.getString(TvInputInfo.EXTRA_INPUT_ID);
        }
        String inputId = context.getSharedPreferences(Constants.PREFERENCE_TVCHANNELS,
                Context.MODE_PRIVATE).getString(Constants.BUNDLE_KEY_INPUT_ID, null);

        if(TextUtils.isEmpty(inputId)){
            return getId(context, TvInputService.class);
        }

        return inputId;
    }


    private static String getId(@NonNull Context context, Class serviceClass) {
       return new ComponentName(context.getPackageName(), serviceClass.getName()).flattenToString();
    }
}
