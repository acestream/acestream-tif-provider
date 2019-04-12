package org.acestream.livechannels.model;

import java.io.Serializable;

public class ContentDescriptor implements Serializable {
    public String type = null;
    public String id = null;

    @Override
    public String toString(){
        return type + "=" + id;
    }
}
