/*
 * Copyright (C) 2015 Actor LLC. <https://actor.im>
 */

package im.actor.runtime.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import im.actor.runtime.Runtime;
import im.actor.runtime.actors.ThreadPriority;
import im.actor.runtime.android.threading.AndroidDispatcher;
import im.actor.runtime.android.threading.AndroidImmediateDispatcher;
import im.actor.runtime.android.time.SntpClient;
import im.actor.runtime.generic.GenericThreadingProvider;
import im.actor.runtime.Log;
import im.actor.runtime.threading.Dispatcher;
import im.actor.runtime.threading.ImmediateDispatcher;

public class AndroidThreadingProvider extends GenericThreadingProvider {

    private static final String PROPS = "time_sync.ini";

    private SharedPreferences preference;
    private long syncDelta;
    private String serverHost;

    public AndroidThreadingProvider() {
        this.serverHost = "europe.pool.ntp.org";

        Runtime.dispatch(() -> {
            this.preference = AndroidContext.getContext().getSharedPreferences(PROPS, Context.MODE_PRIVATE);
            this.syncDelta = preference.getLong("delta", syncDelta);

            invalidateSync();

            IntentFilter timeChangedFilter = new IntentFilter();
            timeChangedFilter.addAction(Intent.ACTION_TIME_CHANGED);
            timeChangedFilter.addAction(Intent.ACTION_DATE_CHANGED);
            AndroidContext.getContext().registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d("AndroidClockSync", "Time changed: invalidating sync");
                    invalidateSync();
                }
            }, timeChangedFilter);
        });
    }

    // TODO: Better invalidation
    private void invalidateSync() {
        Log.d("AndroidClockSync", "Starting sync...");
        new Thread() {
            @Override
            public void run() {
                SntpClient client = new SntpClient();
                while (!client.requestTime(serverHost, 10000)) {

                }
                syncDelta = client.getClockOffset();
                preference.edit().putLong("delta", syncDelta).commit();
                Log.d("AndroidClockSync", "Synced. Time delta: " + syncDelta + " ms");
            }
        }.start();
    }

    @Override
    public long getSyncedCurrentTime() {
        return System.currentTimeMillis() + syncDelta;
    }

    @Override
    public Dispatcher createDispatcher(String name) {
        return new AndroidDispatcher(name);
    }

    @Override
    public ImmediateDispatcher createImmediateDispatcher(String name, ThreadPriority priority) {
        return new AndroidImmediateDispatcher(name, priority);
    }
}
