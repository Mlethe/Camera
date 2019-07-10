package com.game.test.camera;

public interface SaveListener {
    void onSave(String filepath);
    void onError(Exception e);
}
