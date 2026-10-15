package org.psprecomp.ge2;

final class NativeBridge {
    static {
        System.loadLibrary("ge2_native");
    }

    private NativeBridge() {}

    static native String getStatus();
}
