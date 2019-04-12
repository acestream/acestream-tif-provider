package org.acestream.livechannels.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.acestream.engine.controller.Engine;
import org.acestream.livechannels.Constants;
import org.acestream.livechannels.model.AppContext;

import java.util.List;

/**
 * Start engine after reboot
 */
public class BootReceiver extends BroadcastReceiver{
    @Override
    public void onReceive(Context context, Intent intent) {
        Engine engine = AppContext.getEngineFactory().getInstance();
        if(engine != null) {
            engine.startEngine();
        }
    }
}
