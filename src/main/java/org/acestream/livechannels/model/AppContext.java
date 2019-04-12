/**
 * Global app context which can be initialized from other modules.
 */
package org.acestream.livechannels.model;

import org.acestream.engine.controller.Engine;
import org.acestream.livechannels.tvinput.BaseSession;
import org.acestream.livechannels.tvinput.VlcSession;

public class AppContext {
    private static Engine.Factory mEngineFactory = null;
    private static Class<? extends BaseSession> sSessionClass = VlcSession.class;

    public static void setEngineFactory(Engine.Factory factory) {
        mEngineFactory = factory;
    }

    public static Engine.Factory getEngineFactory() {
        return mEngineFactory;
    }

    public static Class<? extends BaseSession> getSessionClass() {
        return sSessionClass;
    }

    public static void setSessionClass(Class<? extends BaseSession> clazz) {
        sSessionClass = clazz;
    }
}
