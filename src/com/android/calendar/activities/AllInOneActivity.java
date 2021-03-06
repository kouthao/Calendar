/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.calendar.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.calendar.agenda.AgendaFragment;
import com.android.calendar.alerts.AlertService;
import com.android.calendar.customize.ImageTextView;
import com.android.calendar.event.EventInfoActivity;
import com.android.calendar.helper.CalendarController;
import com.android.calendar.helper.CalendarController.EventHandler;
import com.android.calendar.helper.CalendarController.EventInfo;
import com.android.calendar.helper.CalendarController.EventType;
import com.android.calendar.helper.CalendarController.ViewType;
import com.android.calendar.kr.big.BigCalendarView;
import com.android.calendar.kr.big.MonthViewFragmentBig;
import com.android.calendar.kr.common.CalendarView;
import com.android.calendar.kr.day.DayViewFragment;
import com.android.calendar.kr.dialogs.CustomDatePickerDialog;
import com.android.calendar.kr.general.GeneralCalendarView;
import com.android.calendar.kr.general.MonthViewFragmentGeneral;
import com.android.calendar.kr.standard.MonthViewFragmentStandard;
import com.android.calendar.kr.vertical.MonthViewFragmentVertical;
import com.android.calendar.kr.vertical.VerticalCalendarView;
import com.android.calendar.kr.year.YearViewFragment;
import com.android.calendar.settings.GeneralPreferences;
import com.android.calendar.settings.SettingsActivity;
import com.android.calendar.utils.Utils;
import com.android.calendar.views.ActionBarHeader;
import com.android.kr_common.Time;
import com.android.krcalendar.R;

import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;

import java.io.File;
import java.util.List;
import java.util.Objects;

import static android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY;
import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;
import static com.android.calendar.utils.Utils.CALENDAR_TYPE1;
import static com.android.calendar.utils.Utils.CALENDAR_TYPE2;
import static com.android.calendar.utils.Utils.CALENDAR_TYPE3;
import static com.android.calendar.utils.Utils.CALENDAR_TYPE4;

/**
 * ???????????? activity
 */
public class AllInOneActivity extends AppCompatActivity implements EventHandler,
        OnSharedPreferenceChangeListener, View.OnClickListener{

    private static final String TAG = "AllInOneActivity";
    private static final boolean DEBUG = false;

    private static final int VIEW_CALENDAR = 101;
    public static final String SELECTED_TIME = "selected_time";

    private static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";
    private static final String BUNDLE_KEY_RESTORE_VIEW = "key_restore_view";
    private static final String BUNDLE_KEY_RESTORE_PREV_VIEW = "key_restore_prev_view";

    private static final String BUNDLE_KEY_RESTORE_START_TIME = "key_restore_start_time";
    private static final String BUNDLE_KEY_RESTORE_END_TIME = "key_restore_end_time";
    private static final String BUNDLE_KEY_RESTORE_QUERY = "key_restore_query";

    private long mStartTimeMillis = 0;
    private long mEndTimeMillis = 0;
    private String mQuery = "";

    private static final int HANDLER_KEY = 0;
    private static final int PERMISSIONS_REQUEST_WRITE_CALENDAR = 0;

    /**
     * ?????? ?????????????????? MainActivity??? ?????? ????????? ???????????? ??????????????????.
     * {@link #onCreate}?????? this??? ???????????? {@link #onDestroy}?????? null??? ????????????.
     */
    private static AllInOneActivity mMainActivity;

    //?????? ??????/?????? ?????? ???????????????
    public String[] PERMISSION_LIST = {
            Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR
    };

    //?????? ??????????????? ???????????? listener
    BroadcastReceiver mTimeTickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            FragmentManager fm = getSupportFragmentManager();
            List<Fragment> fragmentList = fm.getFragments();
            if(!fragmentList.isEmpty()) {
                Fragment fragment = fragmentList.get(0);
                if(fragment instanceof EventHandler) {
                    ((EventHandler)fragment).minuteChanged();
                }
            }
        }
    };

    //Calendar?????? ??????
    private CalendarController mController;

    //??????????????? ??????????????? ???????????? observer
    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            eventsChanged();
        }
    };

    private ContentResolver mContentResolver;
    private int mPreviousView;  //????????? ??????(???,???,???,????????????)
    private int mCurrentView;   //????????? ??????(???,???,???,????????????)
    private boolean mUpdateOnResume = false;    //OnResume?????? fragment??? ?????? ???????????????????

    //Event??? ????????? ????????? ???????????? ?????? ???????????? ??????
    EventInfo mEventInfo;

    private long mViewEventId = -1;
    private long mIntentEventStartMillis = -1;
    private long mIntentEventEndMillis = -1;
    private boolean mIntentAllDay = false;

    //Bottom bar ?????????
    ImageTextView mBtnNewEvent;
    ImageTextView mBtnAgenda;
    ImageTextView mBtnCalculate;
    ImageTextView mBtnGoDate;
    ImageTextView mBtnSettings;

    //?????? ???????????? ???????????? ??????????????? ???????????? ?????? ???????????? ??????
    boolean mDialogOpened = false;

    //???, ???, ???, ???????????? ?????? ?????? View
    ActionBarHeader mActionBarHeader;

    @Override
    protected void onCreate(Bundle icicle) {
        if(Utils.isDayTheme())
            setTheme(R.style.CalendarAppThemeDay);
        else
            setTheme(R.style.CalendarAppThemeNight);

        super.onCreate(icicle);

        mMainActivity = this;

        //CalendarController????????? ??????
        mController = CalendarController.getInstance(this);

        //Notification Chanel?????? ??????
        AlertService.createChannels(this);

        //????????? ?????????????????? ??????
        checkAppPermissions();

        //Intent??? Bundle????????? time, view type?????? ?????????.
        long timeMillis = -1;
        int viewType = -1;
        int prevView = -1;
        final Intent intent = getIntent();

        if (icicle != null) {
            //Bundle????????? ????????? ??????
            timeMillis = icicle.getLong(BUNDLE_KEY_RESTORE_TIME);
            viewType = icicle.getInt(BUNDLE_KEY_RESTORE_VIEW, -1);
            prevView = icicle.getInt(BUNDLE_KEY_RESTORE_PREV_VIEW, -1);

            if(viewType == ViewType.AGENDA) {
                mStartTimeMillis = icicle.getLong(BUNDLE_KEY_RESTORE_START_TIME, 0);
                mEndTimeMillis = icicle.getLong(BUNDLE_KEY_RESTORE_END_TIME, 0);
                mQuery = icicle.getString(BUNDLE_KEY_RESTORE_QUERY);
            }
        } else {
            //Intent????????? ????????? ??????
            String action = intent.getAction();
            if (Intent.ACTION_VIEW.equals(action)) {
                timeMillis = parseViewAction(intent);
            }

            if (timeMillis == -1) {
                timeMillis = Utils.timeFromIntentInMillis(intent);
            }
        }

        if (viewType == -1 || viewType > ViewType.MAX_VALUE) {
            viewType = ViewType.MONTH;
        }
        Time t = new Time();
        t.set(timeMillis);

        //View ??????
        setContentView(R.layout.all_in_one_material);

        //Bottom bar ??????????????? click?????? ??????
        mBtnNewEvent = findViewById(R.id.go_new_event);
        mBtnAgenda = findViewById(R.id.go_agenda);
        mBtnCalculate = findViewById(R.id.go_calculate_date);
        mBtnGoDate = findViewById(R.id.go_date);
        mBtnSettings = findViewById(R.id.go_settings);

        mBtnNewEvent.setOnClickListener(this);
        mBtnAgenda.setOnClickListener(this);
        mBtnCalculate.setOnClickListener(this);
        mBtnGoDate.setOnClickListener(this);
        mBtnSettings.setOnClickListener(this);

        Utils.addCommonTouchListener(mBtnNewEvent);
        Utils.addCommonTouchListener(mBtnAgenda);
        Utils.addCommonTouchListener(mBtnCalculate);
        Utils.addCommonTouchListener(mBtnGoDate);
        Utils.addCommonTouchListener(mBtnSettings);

        mActionBarHeader = findViewById(R.id.actionbar_header);
        mActionBarHeader.initialize();

        //?????? Frame??? Fragment??????
        initFragments(timeMillis, viewType, prevView);

        //Preference???????????? ?????? listener??????
        SharedPreferences prefs = GeneralPreferences.Companion.getSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        //????????? ???????????????????????? ContentResolver??? ???????????? Observer??? ????????????.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED) {
            mContentResolver = getContentResolver();

            mContentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI,
                    true, mObserver);
        }
    }

    /**
     * ????????? ???????????? ???????????? ???????????? dialog??? ????????????.
     */
    private void checkAppPermissions() {
        if(!Utils.isPermissionGranted(this, PERMISSION_LIST)) {
            ActivityCompat.requestPermissions(this,PERMISSION_LIST,
                    PERMISSIONS_REQUEST_WRITE_CALENDAR);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NotNull String[] permissions, @NotNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_WRITE_CALENDAR) {//???????????? ??????????????? ???????????????
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                //????????? ?????????????????? ????????????.
                mController.sendEvent(EventType.CALENDAR_PERMISSION_GRANTED, null, null, -1, ViewType.CURRENT,
                        0);
                onPermissionGranted();
            }

            //???????????? ??????????????? ???????????????
            else {
                //`?????? ?????? ??????`??? check ????????????
                if (Utils.shouldShowRequestPermissionsRationale(this, PERMISSION_LIST)) {
                    //toast??? ?????????.
                    finishApplicationWithToast();
                }
                //`?????? ?????? ??????`??? check ?????????
                else {
                    //App?????????????????? ????????? toast??? ?????????.
                    finishAndShowAppInformationWithToast();
                }
            }
            return;
        }

        //ics, vcs???????????? ?????????.
        cleanupCachedEventFiles();
    }

    /**
     * Toast??? ???????????? app??? ?????????
     */
    public void finishApplicationWithToast() {
        Toast.makeText(getApplicationContext(), R.string.user_rejected_calendar_write_permission, Toast.LENGTH_LONG).show();
        finish();
    }

    /**
     * (1) Toast??? ???????????? app??? ?????????.
     * (2) App??????????????? ????????????.
     */
    public void finishAndShowAppInformationWithToast() {
        Toast.makeText(getApplicationContext(), R.string.user_must_grant_calendar_write_permission, Toast.LENGTH_LONG).show();
        finish();

        //App????????????
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivityForResult(intent, 1);
    }

    /**
     * ?????? app???????????? ??? app??? ???????????? intent????????? ?????? ?????????
     * @param intent ??????
     * @return ????????? ????????????.(?????????)
     */
    private long parseViewAction(final Intent intent) {
        long timeMillis = -1;
        Uri data = intent.getData();
        if (data != null && data.isHierarchical()) {
            List<String> path = data.getPathSegments();
            if (path.size() == 2 && path.get(0).equals("events")) {
                try {
                    mViewEventId = Long.parseLong(Objects.requireNonNull(data.getLastPathSegment()));
                    if (mViewEventId != -1) {
                        mIntentEventStartMillis = intent.getLongExtra(EXTRA_EVENT_BEGIN_TIME, 0);
                        mIntentEventEndMillis = intent.getLongExtra(EXTRA_EVENT_END_TIME, 0);
                        mIntentAllDay = intent.getBooleanExtra(EXTRA_EVENT_ALL_DAY, false);
                        timeMillis = mIntentEventStartMillis;
                    }
                } catch (NumberFormatException e) {
                    //??????????????? ?????????.
                }
            }
        }
        return timeMillis;
    }

    @Override
    protected void onResume() {
        super.onResume();

        //Event Handler??????
        mController.registerFirstEventHandler(HANDLER_KEY, this);
        //????????? ???????????? ???????????? ?????? ????????? ?????? ?????????.
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (mUpdateOnResume) {
            if (mController.getViewType() != ViewType.AGENDA) {
                initFragments(mController.getTime(), mController.getViewType(), -1);
            }
            mUpdateOnResume = false;
        }

        if (mViewEventId != -1 && mIntentEventStartMillis != -1 && mIntentEventEndMillis != -1) {
            long currentMillis = System.currentTimeMillis();
            long selectedTime = -1;
            if (currentMillis > mIntentEventStartMillis && currentMillis < mIntentEventEndMillis) {
                selectedTime = currentMillis;
            }
            mController.sendEventRelatedEventWithExtra(EventType.VIEW_EVENT, mViewEventId,
                    mIntentEventStartMillis, mIntentEventEndMillis,
                    EventInfo.buildViewExtraLong(mIntentAllDay),
                    selectedTime);
            mViewEventId = -1;
            mIntentEventStartMillis = -1;
            mIntentEventEndMillis = -1;
            mIntentAllDay = false;
        }

        registerReceiver(mTimeTickReceiver,  new IntentFilter(Intent.ACTION_TIME_TICK));
    }


    @Override
    protected void onPause() {
        super.onPause();

        //Event Handler??????
        mController.deregisterEventHandler(HANDLER_KEY);

        //????????? ???????????? ???????????? ?????? ????????? ?????? ?????????.
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Manifest.permission.WRITE_CALENDAR is not granted");
            return;
        }

        unregisterReceiver(mTimeTickReceiver);
    }

    @Override
    public void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BUNDLE_KEY_RESTORE_TIME, mController.getTime());
        outState.putInt(BUNDLE_KEY_RESTORE_VIEW, mCurrentView);
        outState.putInt(BUNDLE_KEY_RESTORE_PREV_VIEW, mPreviousView);

        if (mCurrentView == ViewType.AGENDA) {
            FragmentManager fm = getSupportFragmentManager();
            Fragment f = fm.findFragmentById(R.id.main_pane);

            if(f instanceof AgendaFragment) {
                AgendaFragment fragment = (AgendaFragment) f;
                outState.putLong(BUNDLE_KEY_RESTORE_START_TIME, fragment.getStartMillis());
                outState.putLong(BUNDLE_KEY_RESTORE_END_TIME, fragment.getEndMillis());
                outState.putString(BUNDLE_KEY_RESTORE_QUERY, fragment.getQuery());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mMainActivity = null;

        if(mContentResolver != null)
            mContentResolver.unregisterContentObserver(mObserver);

        SharedPreferences prefs = GeneralPreferences.Companion.getSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        mController.deregisterAllEventHandlers();
        CalendarController.removeInstance(this);

        //ics, vcs cache ????????? ?????????
        cleanupCachedEventFiles();
    }

    /**
     * cache???????????? ??????????????? ????????? ics, vcs???????????? ?????????
     */
    private void cleanupCachedEventFiles() {
        if (!isExternalStorageWritable()) return;
        File cacheDir = getExternalCacheDir();
        if(cacheDir == null) return;

        File[] files = cacheDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String filename = file.getName();
            if (filename.endsWith(".ics") || filename.endsWith(".vcs")) {
                file.delete();
            }
        }
    }

    /**
     * ?????????????????? ??????/???????????? ???????????? ????????????.
     * @return true: ??????/???????????? ??????, false: ??????
     */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private void initFragments(long timeMillis, int viewType, int prevViewType) {
        if (DEBUG) {
            Log.d(TAG, "Initializing to " + timeMillis + " for view " + viewType);
        }
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        setMainPane(ft, R.id.main_pane, viewType, prevViewType, timeMillis, null, true);

        Time t = new Time();
        t.set(timeMillis);

        mController.sendEvent(EventType.GO_TO, t, null, -1, viewType);
    }

    @Override
    public void onBackPressed() {
        //??????????????? ??????
        if(mCurrentView == ViewType.DAY) {
            mController.sendEvent(EventType.GO_TO, null, null, -1, ViewType.MONTH);
        }

        //??????????????? ??????
        else if(mCurrentView == ViewType.YEAR) {
            long timeMillis = -1;

            List<Fragment> fragmentList = getSupportFragmentManager().getFragments();
            if(!fragmentList.isEmpty()){
                Fragment fragment = fragmentList.get(0);
                if(fragment instanceof YearViewFragment) {
                    timeMillis = ((YearViewFragment)fragment).getTimeMillis();
                }
            }

            if(timeMillis < 0) {
                mController.sendEvent(EventType.GO_TO, null, null, -1, ViewType.MONTH);
            }
            else {
                Time time = new Time();
                time.set(timeMillis);
                mController.sendEvent(EventType.GO_TO, time, time, -1, ViewType.MONTH);
            }
        }

        //?????????????????? ??????
        else if (mCurrentView == ViewType.AGENDA) {
            if(mPreviousView == ViewType.DAY && Utils.getCalendarTypePreference(this) != CALENDAR_TYPE1){
                mPreviousView = ViewType.MONTH;
            }

            mController.sendEvent(EventType.GO_TO, null, null, -1, mPreviousView);
        }

        //??????????????? ??????
        else {
            //Fragment back stack??? ???????????? onBackpressed??? ????????? finishAfterTransition??? ????????????.
            FragmentManager fragmentManager = getSupportFragmentManager();
            if(fragmentManager.getBackStackEntryCount() == 0) {
                //??????4?????? back????????? ??????????????? ????????? ??????????????? ??????????????? ????????????.
                if(mCurrentView == ViewType.MONTH) {
                    List<Fragment> fragments = fragmentManager.getFragments();
                    if(!fragments.isEmpty()) {
                        Fragment fragment = fragments.get(0);
                        if(fragment instanceof MonthViewFragmentVertical &&
                                !VerticalCalendarView.VerticalCalendarViewDelegate.isExpanded()) {
                            //????????? ????????? ???????????? back??? ????????? ????????? ?????? ????????????.
                            ((MonthViewFragmentVertical) fragment).getCalendarView().onFlingOrClick();
                            return;
                        }
                    }
                }
                finishAfterTransition();
            }
            else
                super.onBackPressed();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        //??????????????? ???????????????
        if(key.equals(Utils.CALENDAR_TYPE_PREF)){
            mUpdateOnResume = true;
            if(mCurrentView == ViewType.DAY ) {
                if(Utils.getCalendarTypePreference(this) != CALENDAR_TYPE1) {
                    mController.setViewType(ViewType.MONTH);
                }
                else {
                    mController.setViewType(ViewType.DAY);
                }
            }
        }
    }

    /**
     * Bottom bar??? ???????????? visible?????? ??????
     */
    public void updateVisibility(){
        final int viewType = mCurrentView;
        final boolean toAgenda = viewType == ViewType.AGENDA && mPreviousView != ViewType.AGENDA;
        final boolean fromAgenda = viewType != ViewType.AGENDA && mPreviousView == ViewType.AGENDA;
        if(toAgenda || fromAgenda) {
            if (toAgenda) {
                ((ViewGroup) mBtnAgenda.getParent()).setVisibility(View.GONE);
                ((ViewGroup) mBtnCalculate.getParent()).setVisibility(View.GONE);
                ((ViewGroup) mBtnGoDate.getParent()).setVisibility(View.VISIBLE);
            }
            else{
                ((ViewGroup) mBtnAgenda.getParent()).setVisibility(View.VISIBLE);
                ((ViewGroup) mBtnCalculate.getParent()).setVisibility(View.VISIBLE);
                ((ViewGroup) mBtnGoDate.getParent()).setVisibility(View.GONE);
            }
        }

        else if(viewType == ViewType.YEAR) {
            ((ViewGroup)mBtnAgenda.getParent()).setVisibility(View.GONE);
        }
        else if(viewType == ViewType.MONTH) {
            ((ViewGroup)mBtnAgenda.getParent()).setVisibility(View.VISIBLE);
        }

        mActionBarHeader.updateViews();
    }

    public boolean viewToAgenda(){
        return mCurrentView == ViewType.AGENDA && mPreviousView != ViewType.AGENDA;
    }

    public boolean viewFromAgenda(){
        return mCurrentView != ViewType.AGENDA && mPreviousView == ViewType.AGENDA;
    }

    private void setMainPane(
            FragmentTransaction ft, int viewId, int viewType, int prevViewType, long timeMillis, EventInfo eventInfo, boolean force) {

        FragmentManager fragmentManager = getSupportFragmentManager();

        //?????? ???????????? Fragment??? ???????????? ????????? ???????????? ????????? ?????? ?????? ??? ??????????????? ????????? ?????????.
        if (!force && !mUpdateOnResume && mCurrentView == viewType) {
            return;
        }

        //????????? ???????????? transition??? ????????????.
        /*
            ???????????? -> ???, ???
            ???, ??? -> ????????????
            ??? -> ???
            ??? -> ???
            ??? -> ???
            ??? -> ???
         */
        int calendarType = Utils.getCalendarTypePreference(this);
        boolean doTransition =
                (viewType == ViewType.AGENDA && mCurrentView != ViewType.AGENDA) ||
                        (viewType != ViewType.AGENDA && mCurrentView == ViewType.AGENDA) ||
                        (viewType == ViewType.MONTH && mCurrentView == ViewType.DAY) ||
                        (viewType == ViewType.DAY && mCurrentView == ViewType.MONTH) ||
                        (viewType == ViewType.MONTH && mCurrentView == ViewType.YEAR) ||
                        (viewType == ViewType.YEAR && mCurrentView == ViewType.MONTH);

        boolean dayToMonthTransition = viewType == ViewType.MONTH && mCurrentView == ViewType.DAY && calendarType == CALENDAR_TYPE1;
        boolean monthToDayTransition = viewType == ViewType.DAY && mCurrentView == ViewType.MONTH && calendarType == CALENDAR_TYPE1;
        boolean monthToYearTransition = viewType == ViewType.YEAR && mCurrentView == ViewType.MONTH;
        boolean yearToMonthTransition = viewType == ViewType.MONTH && mCurrentView == ViewType.YEAR;
        boolean toAgendaTransition = viewType == ViewType.AGENDA;
        boolean fromAgendaTransition = mCurrentView == ViewType.AGENDA;

        String fragmentTAG = "";

        if (viewType != mCurrentView) {
            //?????????  ViewType ??? ????????????.
            if(prevViewType != -1)
                mPreviousView = prevViewType;

            else if (mCurrentView > 0) {
                mPreviousView = mCurrentView;
            }

            mCurrentView = viewType;
        }

        //Fragment??????
        Fragment frag;

        switch (viewType) {
            case ViewType.AGENDA:
                if(eventInfo == null) {
                    if(mEventInfo != null)
                        frag = new AgendaFragment(timeMillis, mEventInfo.startTime, mEventInfo.endTime, mQuery);
                    else {
                        if(mStartTimeMillis != 0)
                            frag = new AgendaFragment(timeMillis, new Time(mStartTimeMillis), new Time(mEndTimeMillis), mQuery);
                        else
                            frag = new AgendaFragment();
                    }
                }
                else {
                    frag = new AgendaFragment(timeMillis, eventInfo.startTime, eventInfo.endTime, mQuery);
                    mEventInfo = eventInfo;
                }
                fragmentTAG = "AGENDA";
                break;
            case ViewType.YEAR:
                frag = new YearViewFragment(timeMillis);
                fragmentTAG = "YEAR";
                break;
            case ViewType.DAY:
                frag = new DayViewFragment(timeMillis);
                fragmentTAG = "DAY";
                break;
            case ViewType.MONTH:
            default:
                if(calendarType == CALENDAR_TYPE1) {
                    frag = new MonthViewFragmentStandard(timeMillis);
                }
                else if(calendarType == CALENDAR_TYPE2) {
                    frag = new MonthViewFragmentGeneral(timeMillis);
                }
                else if(calendarType == CALENDAR_TYPE3) {
                    frag = new MonthViewFragmentBig(timeMillis);
                }
                else if(calendarType == CALENDAR_TYPE4){
                    frag = new MonthViewFragmentVertical(timeMillis);
                }
                else {
                    frag = new MonthViewFragmentStandard(timeMillis);
                }

                fragmentTAG = "MONTH";
                break;
        }

        //ft??? null???????????? transition??? ????????????.
        if (ft == null) {
            ft = fragmentManager.beginTransaction();
        }
        else {
            doTransition = false;
        }

        Utils.setDayToMonthTransition(false);
        Utils.setMonthToDayTransition(false);
        Utils.setMonthToYearTransition(false);
        Utils.setYearToMonthTransition(false);
        Utils.setToAgendaTransition(false);
        Utils.setFromAgendaTransition(false);
        Utils.setTodayBothVisible(false);

        //Fragment?????? transition??? ????????? ???????????? transition animation??? ??????.
        if (doTransition) {
            if(monthToDayTransition) {
                Utils.setMonthToDayTransition(true);
                ft.setCustomAnimations(R.animator.day_enter, R.animator.month_exit);
            }
            else if(dayToMonthTransition) {
                Utils.setDayToMonthTransition(true);
                ft.setCustomAnimations(R.animator.month_enter, R.animator.day_exit);
            }
            else if(monthToYearTransition) {
                Utils.setMonthToYearTransition(true);
                ft.setCustomAnimations(R.animator.slide_from_left_ym, R.animator.slide_to_right_ym);

                //Controller??? ????????? ????????? ????????? `??????`????????? ?????? fade animation??? ?????? ?????????.
                final DateTime nowTime = DateTime.now();
                final DateTime dateTime = new DateTime(mController.getTime());
                if(nowTime.getYear() != dateTime.getYear())
                    Utils.setTodayBothVisible(true);
            }
            else if(yearToMonthTransition) {
                Utils.setYearToMonthTransition(true);
                ft.setCustomAnimations(R.animator.slide_from_right_ym, R.animator.slide_to_left_ym);

                //Controller??? ????????? ????????? ????????? `??????`????????? ?????? fade animation??? ?????? ?????????.
                final DateTime nowTime = DateTime.now();
                final DateTime dateTime = new DateTime(mController.getTime());
                if(nowTime.getYear() != dateTime.getYear())
                    Utils.setTodayBothVisible(true);
            }
            else if(toAgendaTransition) {
                Utils.setToAgendaTransition(true);
                ft.setCustomAnimations(R.animator.slide_from_right_a, R.animator.slide_to_left_a);
            }
            else if(fromAgendaTransition) {
                Utils.setFromAgendaTransition(true);
                ft.setCustomAnimations(R.animator.slide_from_left_a, R.animator.slide_to_right_a);
            }
            else {
                ft.setCustomAnimations(R.animator.fade_in, R.animator.fade_out);
            }
        }

        ft.replace(viewId, frag, fragmentTAG);
        ft.commit();

        //key??? EventHandler??? ????????????.
        mController.registerEventHandler(viewId, (EventHandler) frag);
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.GO_TO | EventType.VIEW_EVENT | EventType.LAUNCH_MONTH_PICKER;
    }

    @Override
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.GO_TO) {
            mEventInfo = event;

            if(event.selectedTime != null) {
                setMainPane(
                        null, R.id.main_pane, event.viewType, -1, event.selectedTime.toMillis(false), event, false);
            }
            else {
                setMainPane(
                        null, R.id.main_pane, event.viewType, -1, event.startTime.toMillis(false), event, false);
            }
        }

        else if (event.eventType == EventType.VIEW_EVENT) {
            //?????????????????? activity??? ??????.
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setClass(this, EventInfoActivity.class);

            Uri eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, event.id);
            intent.setData(eventUri);       //Event Uri
            intent.putExtra(EXTRA_EVENT_BEGIN_TIME, event.startTime.toMillis(false));   //????????????
            intent.putExtra(EXTRA_EVENT_END_TIME, event.endTime.toMillis(false));       //????????????
            startActivity(intent);
        }

        else if(event.eventType == EventType.LAUNCH_MONTH_PICKER) {
            if(event.selectedTime != null) {
                setMainPane(
                        null, R.id.main_pane, event.viewType, -1, event.selectedTime.toMillis(false), event, false);
            }
        }
    }

    //??????????????? ??????????????????
    public void onPermissionGranted() {
        //content observer??? ????????????.
        mContentResolver = getContentResolver();
        mContentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI,
                true, mObserver);
    }

    @Override
    public void eventsChanged() {
        mController.sendEvent(EventType.EVENTS_CHANGED, null, null, -1, ViewType.CURRENT);
    }

    @Override
    public void minuteChanged() {
    }

    /**
     * ???????????? ??????????????? ??????
     */
    @Override
    public void onClick(View v) {
        //Transition animation??? ??????????????? ??????????????? ?????????.
        if(Utils.isOnTransition()){
            return;
        }

        if (v == mBtnNewEvent)      //`??? ??????`
            onSelectNewEvent();
        else if (v == mBtnAgenda)   //`????????????`
            onSelectAgenda();
        else if (v == mBtnCalculate)    //`????????????`
            onSelectCalculate();
        else if (v == mBtnGoDate)       //`????????????`
            onSelectGoDate();
        else if (v == mBtnSettings)     //`??????`
            onSelectSettings();
    }

    /**
     * `?????????`????????? ????????????
     */
    public void onSelectNewEvent(){
        Time t = new Time();
        t.set(mController.getTime());

        //????????? ?????????????????? ??????, ?????? ?????? ??????, ????????? ????????????.
        DateTime now = DateTime.now();
        if(now.getYear() == t.year && now.getMonthOfYear() == t.month + 1 && now.getDayOfMonth() == t.monthDay) {
            t.hour = now.getHourOfDay();
            t.minute = now.getMinuteOfHour();
        }
        //?????? ?????? ?????????????????? 0???, 0????????? ????????????
        else {
            t.hour = 0;
            t.minute = 0;
        }
        t.second = 0;   //0???

        //30???????????? ?????? ?????? 0?????????, 0 - 30?????? ??? ?????? 30????????? ??????
        //(10??? 20??? -> 10??? 30???, 10??? 40??? -> 11??? 0???)
        if (t.minute > 30) {
            t.plusHours(1);
            t.minute = 0;
        } else if (t.minute > 0 && t.minute < 30) {
            t.minute = 30;
        }
        mController.sendEventRelatedEvent(
                EventType.CREATE_EVENT, -1, t.toMillis(true), 0, -1);
    }

    /**
     * `????????????`????????? ????????????
     */
    public void onSelectAgenda(){
        Time startTime = new Time();
        Time endTime = new Time();
        Time selectedTime = new Time();

        //???????????? 1?????? ?????????????????? ??????????????? ??????/?????????????????? ????????????.
        DateTime dateTime = new DateTime(mController.getTime());
        DateTime firstDate = new DateTime(dateTime.getYear(), dateTime.getMonthOfYear(), 1, 0, 0);
        DateTime lastDate = firstDate.plusMonths(1).minusDays(1);

        startTime.set(firstDate.getMillis());
        endTime.set(lastDate.getMillis());
        selectedTime.set(dateTime.getMillis());

        mController.sendEvent(EventType.GO_TO, startTime, endTime, selectedTime,-1, ViewType.AGENDA,
                0);
    }

    /**
     * `????????????`??? ????????????
     */
    public void onSelectCalculate(){
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(getApplicationContext(), DateCalculateActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(SELECTED_TIME, mController.getTime());
        startActivityForResult(intent, VIEW_CALENDAR);
    }

    /**
     * `????????????`??? ????????????(????????????????????????)
     */
    public void onSelectGoDate(){
        if(mDialogOpened)
            return;

        CustomDatePickerDialog dialog = new CustomDatePickerDialog(new Time(mController.getTime()), this);
        Utils.makeBottomDialog(dialog);

        dialog.setOnDateSelectedListener(new CustomDatePickerDialog.OnDateSelectedListener() {
            @Override
            public void onDateSelected(int year, int monthOfYear, int dayOfMonth) {
                DateTime selectedDate = new DateTime(year, monthOfYear + 1, dayOfMonth, 0, 0);
                mController.setTime(selectedDate.getMillis());

                FragmentManager fm = getSupportFragmentManager();
                Fragment f = fm.findFragmentById(R.id.main_pane);
                if(f instanceof AgendaFragment){
                    ((AgendaFragment)f).onSelectDate(selectedDate);
                }
            }

            @Override
            public void onDateSelected(int year, int monthOfYear, int dayOfMonth, boolean isStart) {
            }
        });

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mDialogOpened = false;
            }
        });

        mDialogOpened = true;
        dialog.show();
    }

    /**
     * `??????`????????? ????????????
     */
    public void onSelectSettings(){
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(getApplicationContext(), SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == VIEW_CALENDAR) {
            if(resultCode == RESULT_OK) {
                assert data != null;
                long timeMillis = data.getLongExtra(SELECTED_TIME, 0);
                Time time = new Time(timeMillis);
                mController.sendEvent(CalendarController.EventType.GO_TO, time, time, time, -1, CalendarController.ViewType.CURRENT,
                        CalendarController.EXTRA_GOTO_DATE);
            }
        }
    }

    /* ???, ???, ??? ?????? ?????? listener ?????? ??????*/
    public void addYearChangeListeners(YearViewFragment yearViewFragment){
        yearViewFragment.addYearChangeListener(mActionBarHeader);
    }

    public void addMonthChangeListeners(CalendarView calendarView){
        calendarView.addMonthChangeListener(mActionBarHeader);
    }

    public void addMonthChangeListeners(VerticalCalendarView calendarView){
        calendarView.addMonthChangeListener(mActionBarHeader);
    }

    public void addMonthChangeListeners(BigCalendarView calendarView){
        calendarView.addMonthChangeListener(mActionBarHeader);
    }

    public void addDayChangeListeners(DayViewFragment dayViewFragment){
        dayViewFragment.addDayChangeListener(mActionBarHeader);
    }

    public void addDayChangeListeners(VerticalCalendarView calendarView){
        calendarView.addDayChangeListener(mActionBarHeader);
    }

    public void addDayChangeListeners(BigCalendarView calendarView){
        calendarView.addDayChangeListener(mActionBarHeader);
    }

    public void addDayChangeListeners(GeneralCalendarView calendarView){
        calendarView.addDayChangeListener(mActionBarHeader);
    }

    public ActionBarHeader getActionBarHeader(){
        return mActionBarHeader;
    }

    /**
     * @param context
     * @return context????????? AllInOneActivity??? ????????? ????????????
     */
    public static AllInOneActivity getMainActivity(Context context) {
        if (context instanceof AllInOneActivity) {
            return (AllInOneActivity) context;
        }
        return ((AllInOneActivity) ((ContextWrapper) context).getBaseContext());
    }

    public CalendarController getCalendarController() {
        return mController;
    }

    public static AllInOneActivity getMainActivity() {
        return mMainActivity;
    }
}
