package com.example.myterminalemulator;

import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import com.example.myterminalemulator.util.TermSettings;

class BoundSession extends GenericTermSession {
    private final String issuerTitle;

    private boolean fullyInitialized;

    BoundSession(ParcelFileDescriptor ptmxFd, TermSettings settings, String issuerTitle) {
        super(ptmxFd, settings, true);

        this.issuerTitle = issuerTitle;

        Log.d("BoundSession", "issuerTitle: " + issuerTitle);

        setTermIn(new ParcelFileDescriptor.AutoCloseInputStream(ptmxFd));
        setTermOut(new ParcelFileDescriptor.AutoCloseOutputStream(ptmxFd));
    }

    @Override
    public String getTitle() {
        final String extraTitle = super.getTitle();

        return TextUtils.isEmpty(extraTitle)
               ? issuerTitle
               : issuerTitle + " — " + extraTitle;
    }

    @Override
    public void initializeEmulator(int columns, int rows) {
        super.initializeEmulator(columns, rows);

        Log.d("BoundSession", "initializeEmulator columns: " + columns + "   rows: " + rows);
        fullyInitialized = true;
    }

    @Override
    boolean isFailFast() {
        return !fullyInitialized;
    }
}
