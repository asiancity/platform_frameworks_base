/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.telephony.ims.feature;

import android.annotation.IntDef;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.ims.internal.IImsFeatureStatusCallback;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all IMS features that are supported by the framework.
 * @hide
 */
public abstract class ImsFeature {

    private static final String LOG_TAG = "ImsFeature";

    /**
     * Action to broadcast when ImsService is up.
     * Internal use only.
     * Only defined here separately compatibility purposes with the old ImsService.
     * @hide
     */
    public static final String ACTION_IMS_SERVICE_UP =
            "com.android.ims.IMS_SERVICE_UP";

    /**
     * Action to broadcast when ImsService is down.
     * Internal use only.
     * Only defined here separately for compatibility purposes with the old ImsService.
     * @hide
     */
    public static final String ACTION_IMS_SERVICE_DOWN =
            "com.android.ims.IMS_SERVICE_DOWN";

    /**
     * Part of the ACTION_IMS_SERVICE_UP or _DOWN intents.
     * A long value; the phone ID corresponding to the IMS service coming up or down.
     * Only defined here separately for compatibility purposes with the old ImsService.
     * @hide
     */
    public static final String EXTRA_PHONE_ID = "android:phone_id";

    // Invalid feature value
    public static final int INVALID = -1;
    // ImsFeatures that are defined in the Manifests. Ensure that these values match the previously
    // defined values in ImsServiceClass for compatibility purposes.
    public static final int EMERGENCY_MMTEL = 0;
    public static final int MMTEL = 1;
    public static final int RCS = 2;
    // Total number of features defined
    public static final int MAX = 3;

    // Integer values defining the state of the ImsFeature at any time.
    @IntDef(flag = true,
            value = {
                    STATE_NOT_AVAILABLE,
                    STATE_INITIALIZING,
                    STATE_READY,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ImsState {}
    public static final int STATE_NOT_AVAILABLE = 0;
    public static final int STATE_INITIALIZING = 1;
    public static final int STATE_READY = 2;

    private List<INotifyFeatureRemoved> mRemovedListeners = new ArrayList<>();
    private IImsFeatureStatusCallback mStatusCallback;
    private @ImsState int mState = STATE_NOT_AVAILABLE;
    private int mSlotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    private Context mContext;

    public interface INotifyFeatureRemoved {
        void onFeatureRemoved(int slotId);
    }

    public void setContext(Context context) {
        mContext = context;
    }

    public void setSlotId(int slotId) {
        mSlotId = slotId;
    }

    public void addFeatureRemovedListener(INotifyFeatureRemoved listener) {
        synchronized (mRemovedListeners) {
            mRemovedListeners.add(listener);
        }
    }

    public void removeFeatureRemovedListener(INotifyFeatureRemoved listener) {
        synchronized (mRemovedListeners) {
            mRemovedListeners.remove(listener);
        }
    }

    // Not final for testing.
    public void notifyFeatureRemoved(int slotId) {
        synchronized (mRemovedListeners) {
            mRemovedListeners.forEach(l -> l.onFeatureRemoved(slotId));
            onFeatureRemoved();
        }
    }

    public int getFeatureState() {
        return mState;
    }

    protected final void setFeatureState(@ImsState int state) {
        if (mState != state) {
            mState = state;
            notifyFeatureState(state);
        }
    }

    // Not final for testing.
    public void setImsFeatureStatusCallback(IImsFeatureStatusCallback c) {
        mStatusCallback = c;
        // If we have just connected, send queued status.
        notifyFeatureState(mState);
    }

    /**
     * Internal method called by ImsFeature when setFeatureState has changed.
     * @param state
     */
    private void notifyFeatureState(@ImsState int state) {
        if (mStatusCallback != null) {
            try {
                Log.i(LOG_TAG, "notifying ImsFeatureState=" + state);
                mStatusCallback.notifyImsFeatureStatus(state);
            } catch (RemoteException e) {
                mStatusCallback = null;
                Log.w(LOG_TAG, "Couldn't notify feature state: " + e.getMessage());
            }
        }
        sendImsServiceIntent(state);
    }

    /**
     * Provide backwards compatibility using deprecated service UP/DOWN intents.
     */
    private void sendImsServiceIntent(@ImsState int state) {
        if(mContext == null || mSlotId == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            return;
        }
        Intent intent;
        switch (state) {
            case ImsFeature.STATE_NOT_AVAILABLE:
            case ImsFeature.STATE_INITIALIZING:
                intent = new Intent(ACTION_IMS_SERVICE_DOWN);
                break;
            case ImsFeature.STATE_READY:
                intent = new Intent(ACTION_IMS_SERVICE_UP);
                break;
            default:
                intent = new Intent(ACTION_IMS_SERVICE_DOWN);
        }
        intent.putExtra(EXTRA_PHONE_ID, mSlotId);
        mContext.sendBroadcast(intent);
    }

    /**
     * Called when the feature is being removed and must be cleaned up.
     */
    public abstract void onFeatureRemoved();
}
