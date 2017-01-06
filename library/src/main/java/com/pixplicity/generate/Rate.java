/*
 * Copyright (c) 2017    Mathijs Lagerberg, Pixplicity BV
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

package com.pixplicity.generate;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;

import java.util.concurrent.TimeUnit;

/**
 * When your app has launched a couple of times, this class will ask to give your app a rating on
 * the Play Store. If the user does not want to rate your app and indicates a complaint, you have
 * the option to redirect them to a feedback link.
 * <p>
 * To use, call the following on every app start (or when appropriate):<br />
 * <code>
 * Rate mRate = new Rate.Builder(context)
 * .setTriggerCount(10)
 * .setMinimumInstallTime(TimeUnit.DAYS.toMillis(7))
 * .setMessage(R.string.my_message_text)
 * .setSnackBarParent(view)
 * .build();
 * mRate.launched();
 * </code>
 * When it is a good time to show a rating request, call:
 * <code>
 * mRate.check();
 * </code>
 * </p>
 * <p>
 */
public final class Rate {

    private static final String PREFS_NAME = "pirate";
    private static final String KEY_INT_LAUNCH_COUNT = "launch_count";
    private static final String KEY_BOOL_ASKED = "asked";
    private static final String KEY_LONG_FIRST_LAUNCH = "first_launch";
    private static final int DEFAULT_COUNT = 6;
    private static final int DEFAULT_REPEAT_COUNT = 30;
    private static final long DEFAULT_INSTALL_TIME = TimeUnit.DAYS.toMillis(5);
    private static final boolean DEFAULT_CHECKED = true;

    private final SharedPreferences mPrefs;
    private final String mPackageName;
    private final Context mContext;
    private CharSequence mMessage, mTextPositive, mTextNegative, mTextCancel, mTextNever;
    private int mTriggerCount = DEFAULT_COUNT;
    private long mMinInstallTime = DEFAULT_INSTALL_TIME;
    private ViewGroup mParentView;
    private OnClickListener mFeedbackAction;

    private Rate(@NonNull Context context) {
        mContext = context;
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mPackageName = context.getPackageName();
        mMessage = context.getString(R.string.please_rate);
        mTextPositive = context.getString(R.string.button_yes);
        mTextNegative = context.getString(R.string.button_feedback);
        mTextCancel = context.getString(R.string.button_no);
        mTextNever = context.getString(R.string.button_dont_ask);
    }

    /**
     * Call this method whenever your app is launched to increase the launch counter. Or whenever
     * the user performs an action that indicates immersion.
     *
     * @return the {@link Rate} instance
     */
    @NonNull
    public Rate launched() {
        Editor editor = mPrefs.edit();
        // Get current launch count
        int count = mPrefs.getInt(KEY_INT_LAUNCH_COUNT, 0);
        // Increment, but only when we're not on a launch point. Otherwise we could miss
        // it when .launched and .check calls are not called exactly alternated
        if (count != mTriggerCount
                && (count - mTriggerCount) % DEFAULT_REPEAT_COUNT != 0) {
            count++;
        }
        editor.putInt(KEY_INT_LAUNCH_COUNT, count).apply();
        // Save first launch timestamp
        if (mPrefs.getLong(KEY_LONG_FIRST_LAUNCH, -1) == -1) {
            editor.putLong(KEY_LONG_FIRST_LAUNCH, System.currentTimeMillis());
        }
        editor.apply();
        return this;
    }

    /**
     * Checks if the app has been launched often enough to ask for a rating, and shows the rating
     * request if so. The rating request can be a SnackBar (preferred) or a dialog.
     *
     * @return If the request is shown or not
     * @see Builder#setSnackBarParent(ViewGroup);
     */
    public boolean check() {
        final int count = mPrefs.getInt(KEY_INT_LAUNCH_COUNT, 0);
        final boolean asked = mPrefs.getBoolean(KEY_BOOL_ASKED, false);
        final long firstLaunch = mPrefs.getLong(KEY_LONG_FIRST_LAUNCH, 0);
        final boolean shouldShowRequest = (count == mTriggerCount
                || (count - mTriggerCount) % DEFAULT_REPEAT_COUNT == 0)
                && !asked
                && System.currentTimeMillis() > firstLaunch + mMinInstallTime;
        if (shouldShowRequest && canRateApp()) {
            showRatingRequest();
        }
        return shouldShowRequest;
    }

    /**
     * Creates an Intent to launch the proper store page. This does not guarantee the Intent can be
     * launched (i.e. that the Play Store is installed).
     *
     * @return The Intent to launch the store.
     */
    @NonNull
    private Intent getStoreIntent() {
        final Uri uri = Uri.parse("market://details?id=" + mPackageName);
        return new Intent(Intent.ACTION_VIEW, uri);
    }

    /**
     * Shows the rating request immediately. For testing.
     *
     * @return the {@link Rate} instance
     */
    @NonNull
    public Rate test() {
        showRatingRequest();
        return this;
    }

    private void showRatingRequest() {
        if (mParentView == null) {
            showRatingDialog();
        } else {
            showRatingSnackbar();
        }
    }

    private void showRatingSnackbar() {
        // Wie is hier nou de snackbar?
        Snackbar.make(mParentView, mMessage, Snackbar.LENGTH_LONG)
                .setAction(R.string.button_yes, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        openPlayStore();
                        saveAsked();
                    }
                })
                .show();
    }

    private void showRatingDialog() {
        LayoutInflater inflater;
        if (mContext instanceof Activity) {
            inflater = ((Activity) mContext).getLayoutInflater();
        } else {
            inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @SuppressLint("InflateParams")
        final ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.in_dialog, null);
        final CheckBox checkBox = (CheckBox) layout.findViewById(R.id.cb_never);
        checkBox.setText(mTextNever);
        checkBox.setChecked(DEFAULT_CHECKED);
        final Button btFeedback = (Button) layout.findViewById(R.id.bt_negative);

        // Build dialog with positive and cancel buttons
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                .setMessage(mMessage)
                .setView(layout)
                .setCancelable(false)
                // OK -> redirect to Play Store and never ask again
                .setPositiveButton(mTextPositive, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface anInterface, int i) {
                        openPlayStore();
                        saveAsked();
                        anInterface.dismiss();
                    }
                })
                // Cancel -> close dialog, ask again later
                .setNeutralButton(mTextCancel, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface anInterface, int i) {
                        if (checkBox.isChecked()) {
                            saveAsked();
                        }
                        anInterface.dismiss();
                    }
                });

        // If possible, make dialog cancelable and remember checkbox state on cancel
        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
            builder
                    .setCancelable(true)
                    .setOnDismissListener(new OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface anInterface) {
                            if (checkBox.isChecked()) {
                                saveAsked();
                            }
                        }
                    });
        }

        // Create dialog before we can continue
        final AlertDialog dialog = builder.create();

        // If negative button action is set, add negative button
        if (mFeedbackAction != null) {
            // Nooope -> redirect to feedback form
            btFeedback.setText(mTextNegative);
            btFeedback.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (checkBox.isChecked()) {
                        saveAsked();
                    }
                    dialog.dismiss();
                    mFeedbackAction.onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
                }
            });
        }

        // Go go go!
        dialog.show();
    }

    private void openPlayStore() {
        final Intent intent = getStoreIntent();
        if (!(mContext instanceof Activity)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        mContext.startActivity(intent);
    }

    /**
     * Checks if the app can be rated, i.e. if the store Intent can be launched, i.e. if the Play
     * Store is installed.
     *
     * @return if the app can be rated
     * @see #getStoreIntent()
     */
    private boolean canRateApp() {
        return canOpenIntent(getStoreIntent());
    }

    /**
     * Checks if the system or any 3rd party app can handle the Intent
     *
     * @param intent the Intent
     * @return if the Intent can be handled by the system
     */
    private boolean canOpenIntent(@NonNull Intent intent) {
        return mContext
                .getPackageManager()
                .queryIntentActivities(intent, 0)
                .size()
                > 0;
    }

    private void saveAsked() {
        mPrefs.edit().putBoolean(KEY_BOOL_ASKED, true).apply();
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class Builder {

        private final Rate mRate;

        public Builder(@NonNull Context context) {
            mRate = new Rate(context);
        }

        /**
         * Set number of times {@link #launched()} should be called before triggering the rating
         * request
         *
         * @param count Number of times (inclusive) to call {@link #launched()} before rating
         *              request should show. Defaults to {@link #DEFAULT_COUNT}
         * @return The current {@link Builder}
         */
        @NonNull
        public Builder setTriggerCount(int count) {
            mRate.mTriggerCount = count;
            return this;
        }

        /**
         * Set amount of time the app should be installed before asking for a rating. Defaults to 5
         * days.
         *
         * @param millis Amount of time in milliseconds the app should be installed before asking a
         *               rating.
         * @return The current {@link Builder}
         */
        @NonNull
        public Builder setMinimumInstallTime(int millis) {
            mRate.mMinInstallTime = millis;
            return this;
        }

        /**
         * Sets the message to show in the rating request.
         *
         * @param message The message that asks the user for a rating
         * @return The current {@link Builder}
         * @see #setMessage(int)
         */
        @NonNull
        public Builder setMessage(@Nullable CharSequence message) {
            mRate.mMessage = message;
            return this;
        }

        /**
         * Sets the message to show in the rating request.
         *
         * @param resId The message that asks the user for a rating
         * @return The current {@link Builder}
         * @see #setMessage(CharSequence)
         */
        @NonNull
        public Builder setMessage(@StringRes int resId) {
            return setMessage(mRate.mContext.getString(resId));
        }

        /**
         * Sets the text to show in the rating request on the positive button.
         *
         * @param message The text on the positive button
         * @return The current {@link Builder}
         * @see #setPositiveButton(int)
         */
        @NonNull
        public Builder setPositiveButton(@Nullable CharSequence message) {
            mRate.mTextPositive = message;
            return this;
        }

        /**
         * Sets the text to show in the rating request on the positive button.
         *
         * @param resId The text on the positive button
         * @return The current {@link Builder}
         * @see #setPositiveButton(CharSequence)
         */
        @NonNull
        public Builder setPositiveButton(@StringRes int resId) {
            return setPositiveButton(mRate.mContext.getString(resId));
        }

        /**
         * Sets the text to show in the rating request on the negative button.
         *
         * @param message The text on the negative button
         * @return The current {@link Builder}
         * @see #setNegativeButton(int)
         */
        @NonNull
        public Builder setNegativeButton(@Nullable CharSequence message) {
            mRate.mTextNegative = message;
            return this;
        }

        /**
         * Sets the text to show in the rating request on the negative button.
         *
         * @param resId The text on the negative button
         * @return The current {@link Builder}
         * @see #setNegativeButton(CharSequence)
         */
        @NonNull
        public Builder setNegativeButton(@StringRes int resId) {
            return setNegativeButton(mRate.mContext.getString(resId));
        }

        /**
         * Sets the text to show in the rating request on the cancel button.
         * Note that this will not be used when using a SnackBar.
         *
         * @param message The text on the cancel button
         * @return The current {@link Builder}
         * @see #setSnackBarParent(ViewGroup)
         * @see #setCancelButton(int)
         */
        @NonNull
        public Builder setCancelButton(@Nullable CharSequence message) {
            mRate.mTextCancel = message;
            return this;
        }

        /**
         * Sets the text to show in the rating request on the cancel button.
         * Note that this will not be used when using a SnackBar.
         *
         * @param resId The text on the cancel button
         * @return The current {@link Builder}
         * @see #setSnackBarParent(ViewGroup)
         * @see #setCancelButton(CharSequence)
         */
        @NonNull
        public Builder setCancelButton(@StringRes int resId) {
            return setCancelButton(mRate.mContext.getString(resId));
        }

        /**
         * Sets the text to show in the rating request on the checkbox.
         *
         * @param message The text on the checkbox
         * @return The current {@link Builder}
         */
        @NonNull
        public Builder setNeverAgainText(@Nullable CharSequence message) {
            mRate.mTextNever = message;
            return this;
        }

        /**
         * Sets the text to show in the rating request on the checkbox.
         *
         * @param resId The text on the checkbox
         * @return The current {@link Builder}
         */
        @NonNull
        public Builder setNeverAgainText(@StringRes int resId) {
            return setNeverAgainText(mRate.mContext.getString(resId));
        }

        /**
         * Sets the Uri to open when the user clicks the feedback button.
         * This can use the scheme `mailto:`, `tel:`, `geo:`, `https:`, etc.
         *
         * @param uri The Uri to open, or {@code null} to hide the feedback button
         * @return The current {@link Builder}
         * @see #setFeedbackAction(OnClickListener)
         */
        @NonNull
        public Builder setFeedbackAction(@Nullable final Uri uri) {
            if (uri == null) {
                mRate.mFeedbackAction = null;
            } else {
                mRate.mFeedbackAction = new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface anInterface, int i) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(uri);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        if (mRate.canOpenIntent(intent)) {
                            mRate.mContext.startActivity(intent);
                        }
                    }
                };
            }
            return this;
        }

        /**
         * Sets the action to perform when the user clicks the feedback button.
         *
         * @param action Callback when the user taps the feedback button, or {@code null} to hide
         *               the feedback button
         * @return The current {@link Builder}
         * @see #setFeedbackAction(Uri)
         */
        @NonNull
        public Builder setFeedbackAction(@Nullable DialogInterface.OnClickListener action) {
            mRate.mFeedbackAction = action;
            return this;
        }

        /**
         * Sets the parent view for a Snackbar. This enables the use of a Snackbar for the rating
         * request instead of the default dialog.
         *
         * @param parent The parent view to put the Snackbar in, or {@code null} to disable the
         *               Snackbar
         * @return The current {@link Builder}
         */
        @NonNull
        public Builder setSnackBarParent(@Nullable ViewGroup parent) {
            mRate.mParentView = parent;
            return this;
        }

        @NonNull
        public Rate build() {
            return mRate;
        }
    }
}