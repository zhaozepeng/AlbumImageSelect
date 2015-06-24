package com.zhao.album;

import java.io.Serializable;

/**
 * @author: zzp
 * @since: 2015-06-16
 */
public class SingleImageModel implements Serializable{
    public String path;
    public boolean isPicked;
    public long date;
    public long id;
    public SingleImageModel(String path, boolean isPicked, long date, long id){
        this.path = path;
        this.isPicked = isPicked;
        this.date = date;
        this.id = id;
    }
    public SingleImageModel(){

    }
    public boolean isThisImage(String path){
        return this.path.equalsIgnoreCase(path);
    }
}
