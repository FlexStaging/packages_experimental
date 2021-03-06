/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.example.notificationlistener;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class NotificationListenerActivity extends ListActivity {
    private static final String LISTENER_PATH = "com.android.example.notificationlistener/" +
            "com.android.example.notificationlistener.Listener";
    private static final String TAG = "NotificationListenerActivity";

    private Button mLaunchButton;
    private TextView mEmptyText;
    private StatusAdaptor mStatusAdaptor;
    private final BroadcastReceiver mRefreshListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "update tickle");
            updateList(intent.getStringExtra(Listener.EXTRA_KEY));
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.long_app_name);
        setContentView(R.layout.main);
        mLaunchButton = (Button) findViewById(R.id.launch_settings);
        mEmptyText = (TextView) findViewById(android.R.id.empty);
        mStatusAdaptor = new StatusAdaptor(this);
        setListAdapter(mStatusAdaptor);
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mRefreshListener);
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        final IntentFilter intentFilter = new IntentFilter(Listener.ACTION_REFRESH);
        LocalBroadcastManager.getInstance(this).registerReceiver(mRefreshListener, intentFilter);
        updateList(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        checkEnabled();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        checkEnabled();
    }

    private void checkEnabled() {
        String listeners = Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (listeners != null && listeners.contains(LISTENER_PATH)) {
            mLaunchButton.setText(R.string.launch_to_disable);
            mEmptyText.setText(R.string.waiting_for_content);
        } else {
            mLaunchButton.setText(R.string.launch_to_enable);
            mEmptyText.setText(R.string.nothing_to_see);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.explanation)
                    .setTitle(R.string.disabled);
            builder.setPositiveButton(R.string.enable_it, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    launchSettings(null);
                }
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.create().show();
        }
    }

    public void launchSettings(View v) {
        startActivityForResult(
                new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), 0);
    }

    public void dismiss(View v) {
        Log.d(TAG, "clicked dismiss ");
        Object tag = v.getTag();
        if (tag instanceof StatusBarNotification) {
            StatusBarNotification sbn = (StatusBarNotification) tag;
            Log.d(TAG, "  on " + sbn.getKey());
            LocalBroadcastManager.getInstance(this).
                    sendBroadcast(new Intent(Listener.ACTION_DISMISS)
                            .putExtra(Listener.EXTRA_KEY, sbn.getKey()));
        }
    }

    public void launch(View v) {
        Log.d(TAG, "clicked launch");
        Object tag = v.getTag();
        if (tag instanceof StatusBarNotification) {
            StatusBarNotification sbn = (StatusBarNotification) tag;
            Log.d(TAG, "  on " + sbn.getKey());
            LocalBroadcastManager.getInstance(this).
                    sendBroadcast(new Intent(Listener.ACTION_LAUNCH)
                            .putExtra(Listener.EXTRA_KEY, sbn.getKey()));
        }
    }

    private void updateList(String key) {
        if (mStatusAdaptor.requiresInitialization()) {
            final List<StatusBarNotification> notifications = Listener.getNotifications();
            if (notifications != null) {
                mStatusAdaptor.init(notifications);
            }
        }
        mStatusAdaptor.update(key);
    }

    private class StatusAdaptor extends BaseAdapter {
        private final Context mContext;
        private List<StatusBarNotification> mNotifications;
        private HashMap<String, Long> mKeyToId;
        private HashSet<String> mKeys;
        private long mNextId;
        private HashMap<String, View> mRecycledViews;
        private String mUpdateKey;

        public StatusAdaptor(Context context) {
            mContext = context;
            mKeyToId = new HashMap<String, Long>();
            mKeys = new HashSet<String>();
            mNextId = 0;
            mRecycledViews = new HashMap<String, View>();
        }

        @Override
        public int getCount() {
            return mNotifications == null ? 0 : mNotifications.size();
        }

        @Override
        public Object getItem(int position) {
            return mNotifications.get(position);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            final StatusBarNotification sbn = mNotifications.get(position);
            final String key = sbn.getKey();
            if (!mKeyToId.containsKey(key)) {
                mKeyToId.put(key, mNextId);
                mNextId ++;
            }
            return mKeyToId.get(key);
        }

        @Override
        public View getView(int position, View view, ViewGroup list) {
            if (view == null) {
                view = View.inflate(mContext, R.layout.item, null);
            }
            FrameLayout container = (FrameLayout) view.findViewById(R.id.remote_view);
            View dismiss = view.findViewById(R.id.dismiss);
            StatusBarNotification sbn = mNotifications.get(position);
            View child;
            if (container.getTag() instanceof StatusBarNotification &&
                    container.getChildCount() > 0) {
                // recycle the view
                StatusBarNotification old = (StatusBarNotification) container.getTag();
                if (sbn.getKey().equals(mUpdateKey)) {
                    //this view is out of date, discard it
                    mUpdateKey = null;
                } else {
                    View content = container.getChildAt(0);
                    container.removeView(content);
                    mRecycledViews.put(old.getKey(), content);
                }
            }
            child = mRecycledViews.get(sbn.getKey());
            if (child == null) {
                child = sbn.getNotification().contentView.apply(mContext, null);
            }
            container.setTag(sbn);
            container.removeAllViews();
            container.addView(child);
            dismiss.setVisibility(sbn.isClearable() ? View.VISIBLE : View.GONE);
            dismiss.setTag(sbn);
            return view;
        }

        public void update(String key) {
            if (mNotifications != null) {
                synchronized (mNotifications) {
                    mKeys.clear();
                    for (int i = 0; i < mNotifications.size(); i++) {
                        mKeys.add(mNotifications.get(i).getKey());
                    }
                    mKeyToId.keySet().retainAll(mKeys);
                }
                if (key == null) {
                    mRecycledViews.clear();
                } else {
                    mUpdateKey = key;
                    mRecycledViews.remove(key);
                }
                Log.d(TAG, "notifyDataSetChanged");
                notifyDataSetChanged();
            } else {
                Log.d(TAG, "missed and update");
            }
        }

        public boolean requiresInitialization() {
            return mNotifications == null;
        }

        public void init(List<StatusBarNotification> notifications) {
            mNotifications = notifications;
        }
    }
}
