/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.myterminalemulator;

import android.app.Service;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.app.Notification;
import android.app.PendingIntent;

import com.example.myterminalemulator.emulatorview.TermSession;

import com.example.myterminalemulator.compat.ServiceForegroundCompat;
import com.example.mylibrary.ITerminal;
import com.example.mylibrary.TermExec;
//import com.example.myterminalemulator.libtermexecxxx.v1.*;
import com.example.myterminalemulator.util.SessionList;
import com.example.myterminalemulator.util.TermSettings;

import java.util.UUID;

public class TermService extends Service implements TermSession.FinishCallback
{
    /* Parallels the value of START_STICKY on API Level >= 5 */
    private static final int COMPAT_START_STICKY = 1;

    private static final int RUNNING_NOTIFICATION = 1;
    private ServiceForegroundCompat compat;

    private SessionList mTermSessions;

    public class TSBinder extends Binder {
        TermService getService() {
            Log.i("TermService", "Activity binding to service");
            return TermService.this;
        }
    }
    private final IBinder mTSBinder = new TSBinder();

    @Override
    public void onStart(Intent intent, int flags) {
    }

    /* This should be @Override if building with API Level >=5 */
    public int onStartCommand(Intent intent, int flags, int startId) {
        return COMPAT_START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (TermExec.SERVICE_ACTION_V1.equals(intent.getAction())) {
            Log.i("TermService", "Outside process called onBind()");

            return new RBinder();
        } else {
            Log.i("TermService", "Activity called onBind()");

            return mTSBinder;
        }
    }

    @Override
    public void onCreate() {
        // should really belong to the Application class, but we don't use one...
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Log.d("TermService", "prefs: " + prefs);
        SharedPreferences.Editor editor = prefs.edit();
        String defValue = getDir("HOME", MODE_PRIVATE).getAbsolutePath(); // /data/user/0/com.example.myterminalemulator/app_HOME
        Log.d("TermService", "defValue: " + defValue);
        String homePath = prefs.getString("home_path", defValue);
        Log.d("TermService", "homePath: " + homePath);
        editor.putString("home_path", homePath);
        editor.commit();

        compat = new ServiceForegroundCompat(this);
        mTermSessions = new SessionList();

        /* Put the service in the foreground. */
        /* //常住通知栏的通知
        Notification notification = new Notification(R.drawable.ic_stat_service_notification_icon, getText(R.string.service_notify_text), System.currentTimeMillis());
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        Intent notifyIntent = new Intent(this, TermActivity.class);
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, 0);
        notification.setLatestEventInfo(this, getText(R.string.application_terminal), getText(R.string.service_notify_text), pendingIntent);
        compat.startForeground(RUNNING_NOTIFICATION, notification);
        */
        Log.d(TermDebug.LOG_TAG, "TermService started");
        return;
    }

    @Override
    public void onDestroy() {
        // compat.stopForeground(true); // 2020年2月10日09:50:27
        for (TermSession session : mTermSessions) {
            /* Don't automatically remove from list of sessions -- we clear the
             * list below anyway and we could trigger
             * ConcurrentModificationException if we do */
            session.setFinishCallback(null);
            session.finish();
        }
        mTermSessions.clear();
        return;
    }

    public SessionList getSessions() {
        return mTermSessions;
    }

    public void onSessionFinish(TermSession session) {
        mTermSessions.remove(session);
    }

    private final class RBinder extends ITerminal.Stub {
        @Override
        public IntentSender startSession(final ParcelFileDescriptor pseudoTerminalMultiplexerFd,
                                         final ResultReceiver callback) {
            final String sessionHandle = UUID.randomUUID().toString();
            Log.d("TermService", "sessionHandle: " + sessionHandle);

            // distinct Intent Uri and PendingIntent requestCode must be sufficient to avoid collisions
            final Intent switchIntent = new Intent(RemoteInterface.PRIVACT_OPEN_NEW_WINDOW)
                    .setData(Uri.parse(sessionHandle))
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(RemoteInterface.PRIVEXTRA_TARGET_WINDOW, sessionHandle);

            final PendingIntent result = PendingIntent.getActivity(getApplicationContext(), sessionHandle.hashCode(),
                    switchIntent, 0);

            final PackageManager pm = getPackageManager();
            final String[] pkgs = pm.getPackagesForUid(getCallingUid());
            if (pkgs == null || pkgs.length == 0)
                return null;

            for (String packageName:pkgs) {
                Log.d("TermService", "packageName: " + packageName);
                try {
                    final PackageInfo pkgInfo = pm.getPackageInfo(packageName, 0);

                    final ApplicationInfo appInfo = pkgInfo.applicationInfo;
                    if (appInfo == null)
                        continue;

                    final CharSequence label = pm.getApplicationLabel(appInfo);

                    if (!TextUtils.isEmpty(label)) {
                        final String niceName = label.toString();
                        Log.d("TermService", "niceName: " + niceName);

                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Log.d("TermService", "qqqqqqqqqqqqqqqqqqqqqqqq " );
                                GenericTermSession session = null;
                                try {
                                    final TermSettings settings = new TermSettings(getResources(),
                                            PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));

                                    session = new BoundSession(pseudoTerminalMultiplexerFd, settings, niceName);

                                    mTermSessions.add(session);

                                    session.setHandle(sessionHandle);
                                    session.setFinishCallback(new RBinderCleanupCallback(result, callback));
                                    session.setTitle("");

                                    Log.d("TermService", "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww");

                                    // session.initializeEmulator(80, 24);
                                } catch (Exception whatWentWrong) {
                                    Log.e("TermService", "Failed to bootstrap AIDL session: "
                                            + whatWentWrong.getMessage());

                                    if (session != null)
                                        session.finish();
                                }
                            }
                        });

                        return result.getIntentSender();
                    }
                } catch (PackageManager.NameNotFoundException ignore) {}
            }

            return null;
        }
    }

    private final class RBinderCleanupCallback implements TermSession.FinishCallback {
        private final PendingIntent result;
        private final ResultReceiver callback;

        public RBinderCleanupCallback(PendingIntent result, ResultReceiver callback) {
            this.result = result;
            this.callback = callback;
        }

        @Override
        public void onSessionFinish(TermSession session) {
            result.cancel();

            callback.send(0, new Bundle());

            mTermSessions.remove(session);
        }
    }
}
