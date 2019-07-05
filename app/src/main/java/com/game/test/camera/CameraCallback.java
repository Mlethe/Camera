package com.game.test.camera;

public interface CameraCallback {
    void onCapture(String path);
    void onError(Exception e);
}
