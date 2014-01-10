package com.alwaysallthetime.adnlibutils.db;

public class OEmbedInstances extends MessageEntityInstances {
    private String mType;

    public OEmbedInstances(String type) {
        super();
        mType = type;
    }

    public String getType() {
        return mType;
    }

    @Override
    public String getName() {
        return mType;
    }
}
