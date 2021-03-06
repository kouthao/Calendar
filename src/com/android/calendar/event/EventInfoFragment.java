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

package com.android.calendar.event;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;

import com.android.calendar.event.CalendarEventModel.ReminderEntry;
import com.android.calendar.helper.AsyncQueryService;
import com.android.calendar.helper.CalendarController;
import com.android.calendar.helper.CalendarController.EventInfo;
import com.android.calendar.helper.CalendarController.EventType;
import com.android.calendar.recurrencepicker.EventRecurrenceFormatter;
import com.android.calendar.utils.Utils;
import com.android.calendarcommon2.DateException;
import com.android.calendarcommon2.Duration;
import com.android.calendarcommon2.EventRecurrence;
import com.android.kr_common.Time;
import com.android.krcalendar.R;

import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import static android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY;
import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;
import static com.android.calendar.helper.CalendarController.EVENT_EDIT_ON_LAUNCH;

/**
 * ?????????????????? Fragment
 */
public class EventInfoFragment extends DialogFragment implements
        CalendarController.EventHandler {

    public static final boolean DEBUG = false;
    public static final String TAG = "EventInfoFragment";

    //Activity??? ??????????????? ???????????? ??????????????? ?????? Key????????????
    public static final String BUNDLE_KEY_EVENT_ID = "key_event_id";
    public static final String BUNDLE_KEY_START_MILLIS = "key_start_millis";
    public static final String BUNDLE_KEY_END_MILLIS = "key_end_millis";
    public static final String BUNDLE_KEY_DELETE_DIALOG_VISIBLE = "key_delete_dialog_visible";

    //????????????????????? ?????? ?????? Column ?????????
    static final String[] CALENDARS_PROJECTION = new String[]{
            Calendars._ID,                      // 0
            Calendars.CALENDAR_DISPLAY_NAME,    // 1
            Calendars.OWNER_ACCOUNT,            // 2
    };
    //?????? ??????????????? ????????? column???
    static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;

    //Selection ??????????????? ?????????
    static final String CALENDARS_WHERE = Calendars._ID + "=?";
    static final String CALENDARS_DUPLICATE_NAME_WHERE = Calendars.CALENDAR_DISPLAY_NAME + "=?";
    static final String CALENDARS_VISIBLE_WHERE = Calendars.VISIBLE + "=?";

    //QueryHandler ?????? ????????? Token ??????
    private static final int TOKEN_QUERY_EVENT = 1;
    private static final int TOKEN_QUERY_CALENDARS = 1 << 1;
    private static final int TOKEN_QUERY_DUPLICATE_CALENDARS = 1 << 2;
    private static final int TOKEN_QUERY_REMINDERS = 1 << 3;
    private static final int TOKEN_QUERY_VISIBLE_CALENDARS = 1 << 4;
    private static final int TOKEN_QUERY_COLORS = 1 << 5;
    private static final int TOKEN_QUERY_ALL = TOKEN_QUERY_DUPLICATE_CALENDARS
            | TOKEN_QUERY_CALENDARS | TOKEN_QUERY_EVENT
            | TOKEN_QUERY_REMINDERS | TOKEN_QUERY_VISIBLE_CALENDARS | TOKEN_QUERY_COLORS;

    //?????????????????? ?????? ?????? column?????????
    private static final String[] EVENT_PROJECTION = new String[] {
        Events._ID,                  // 0  DeleteEventHelper ?????? ?????????
        Events.TITLE,                // 1  DeleteEventHelper ?????? ?????????
        Events.RRULE,                // 2  DeleteEventHelper ?????? ?????????
        Events.ALL_DAY,              // 3  DeleteEventHelper ?????? ?????????
        Events.CALENDAR_ID,          // 4  DeleteEventHelper ?????? ?????????
        Events.DTSTART,              // 5  DeleteEventHelper ?????? ?????????
        Events._SYNC_ID,             // 6  DeleteEventHelper ?????? ?????????
        Events.EVENT_TIMEZONE,       // 7  DeleteEventHelper ?????? ?????????
        Events.DESCRIPTION,          // 8
        Events.EVENT_LOCATION,       // 9
        Calendars.CALENDAR_ACCESS_LEVEL, // 10
        Events.CALENDAR_COLOR,       // 11
        Events.EVENT_COLOR,          // 12
        Events.HAS_ATTENDEE_DATA,    // 13
        Events.ORGANIZER,            // 14
        Events.HAS_ALARM,            // 15
        Calendars.MAX_REMINDERS,     // 16
        Calendars.ALLOWED_REMINDERS, // 17
        Events.CUSTOM_APP_PACKAGE,   // 18
        Events.CUSTOM_APP_URI,       // 19
        Events.DTEND,                // 20
        Events.DURATION,             // 21
        Events.ORIGINAL_SYNC_ID      // 22 DeleteEventHelper ?????? ?????????
    };
    //?????? ??????????????? ????????? column???
    private static final int EVENT_INDEX_ID = 0;
    private static final int EVENT_INDEX_TITLE = 1;
    private static final int EVENT_INDEX_RRULE = 2;
    private static final int EVENT_INDEX_ALL_DAY = 3;
    private static final int EVENT_INDEX_CALENDAR_ID = 4;
    private static final int EVENT_INDEX_DTSTART = 5;
    private static final int EVENT_INDEX_DESCRIPTION = 8;
    private static final int EVENT_INDEX_EVENT_COLOR = 12;
    private static final int EVENT_INDEX_HAS_ALARM = 15;
    private static final int EVENT_INDEX_ALLOWED_REMINDERS = 17;
    private static final int EVENT_INDEX_DTEND = 20;
    private static final int EVENT_INDEX_DURATION = 21;

    //??????????????? ?????? ?????? column?????????
    private static final String[] REMINDERS_PROJECTION = new String[] {
        Reminders._ID,      // 0
        Reminders.MINUTES,  // 1
        Reminders.METHOD    // 2
    };
    private static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=?";

    //Fade Animation??? ????????????
    private static final int FADE_IN_TIME = 300;

    //Loading View??? ???????????? ?????? ???????????? ??????
    //?????????????????? ?????????????????? ??? ???????????? LoadingView??? ???????????? ?????????.
    private static final int LOADING_MSG_DELAY = 600;
    private static final int LOADING_MSG_MIN_DISPLAY_TIME = 600;

    public ArrayList<ReminderEntry> mReminders = new ArrayList<>();
    public ArrayList<ReminderEntry> mOriginalReminders = new ArrayList<ReminderEntry>();
    public ArrayList<ReminderEntry> mUnsupportedReminders = new ArrayList<ReminderEntry>();

    private int mCurrentQuery = 0;
    private View mView;
    private Uri mUri;
    private long mEventId;

    //??????, ??????, ?????????????????? ?????? ?????? Cursor???
    private Cursor mCalendarsCursor;
    private Cursor mEventCursor;
    private Cursor mRemindersCursor;

    //????????????, ????????????, ????????????
    private long mStartMillis;
    private long mEndMillis;
    private boolean mAllDay;

    //???????????????????????? ?????????????
    private boolean mDeleteDialogVisible = false;

    //??????????????? ?????? ???????????? DeleteEventHelper ??????
    private DeleteEventHelper mDeleteHelper;

    //??????????????? ????????? ??????????
    private boolean mHasAlarm;

    //???????????? ????????????
    private String mCalendarAllowedReminders;

    //?????? View???
    private TextView mReminderTextView;
    private ScrollView mScrollView;
    private View mLoadingMsgView;
    private ObjectAnimator mAnimateAlpha;
    private long mLoadingMsgStartTime;
    private final Runnable mLoadingMsgAlphaUpdater = new Runnable() {
        @Override
        public void run() {
            //????????? delay ????????? ????????? ??????????????? ???????????? ????????? ?????? Loading View ??? ????????????.
            if (!mAnimateAlpha.isRunning() && mScrollView.getAlpha() == 0) {
                mLoadingMsgStartTime = System.currentTimeMillis();
                mLoadingMsgView.setAlpha(1);
            }
        }
    };
    private boolean mNoCrossFade = false;  // Used to prevent repeated cross-fade

    /*
     * ???????????? ????????? ?????????(10???, 15???, 20???,...), ??????(10, 15, 20, ...)
     */
    private ArrayList<Integer> mReminderMinuteValues;
    private ArrayList<String> mReminderMinuteLabels;

    /*
     * ???????????? ???????????? ?????????(Notification, Email, SMS, Alarm), ??????(1, 2, 3, 4)
     */
    private ArrayList<Integer> mReminderMethodValues;
    private ArrayList<String> mReminderMethodLabels;

    private QueryHandler mHandler;
    private boolean mIsPaused = true;
    private boolean mDismissOnResume = false;
    private final Runnable onDeleteRunnable = new Runnable() {
        @Override
        public void run() {
            if (EventInfoFragment.this.mIsPaused) {
                mDismissOnResume = true;
                return;
            }
            if (EventInfoFragment.this.isVisible()) {
                EventInfoFragment.this.dismiss();
            }
        }
    };
    private Activity mActivity;
    private final Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            updateEvent(mView);
        }
    };
    private CalendarController mController;

    public EventInfoFragment(Uri uri, long startMillis, long endMillis,
                             ArrayList<ReminderEntry> reminders) {
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        mUri = uri;
        mStartMillis = startMillis;
        mEndMillis = endMillis;

        if(reminders != null)
            mReminders = reminders;
    }

    //?????????????????? ????????? ?????????
    public EventInfoFragment() {
    }

    public EventInfoFragment(long eventId, long startMillis, long endMillis,
                             ArrayList<ReminderEntry> reminders) {
        this(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), startMillis,
                endMillis, reminders);
        mEventId = eventId;
    }

    /**
     * ???????????????????????? ????????? ????????????.
     * @param r
     * @param resNum Resource Id {@link R.array#reminder_minutes_values}
     * @return ArrayList
     */
    private static ArrayList<Integer> loadIntegerArray(Resources r, int resNum) {
        int[] values = r.getIntArray(resNum);
        int size = values.length;
        ArrayList<Integer> list = new ArrayList<Integer>(size);

        for (int val : values) {
            list.add(val);
        }

        return list;
    }

    /**
     * ???????????????????????? ????????? ????????????.
     * @param r
     * @param resNum Resource Id {@link R.array#reminder_minutes_labels}
     * @return ArrayList
     */
    private static ArrayList<String> loadStringArray(Resources r, int resNum) {
        String[] labels = r.getStringArray(resNum);
        return new ArrayList<>(Arrays.asList(labels));
    }

    private void sendAccessibilityEventIfQueryDone(int token) {
        mCurrentQuery |= token;
    }

    /**
     * Activity?????? ????????? ??????????????????
     */
    @Override
    public void onDetach() {
        super.onDetach();
        mController.deregisterEventHandler(R.layout.event_info);
        mController = null;
    }

    /**
     * Activity??? ??????????????????
     * @param context
     */
    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        mActivity = (Activity)context;
        mController = CalendarController.getInstance(mActivity);
        mController.registerEventHandler(R.layout.event_info, this);

        mHandler = new QueryHandler(context);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        //Activity ??? ??????????????? ???????????? ????????????.
        if (savedInstanceState != null) {
            mDeleteDialogVisible =
                savedInstanceState.getBoolean(BUNDLE_KEY_DELETE_DIALOG_VISIBLE,false);
            mEventId = savedInstanceState.getLong(BUNDLE_KEY_EVENT_ID);
            mStartMillis = savedInstanceState.getLong(BUNDLE_KEY_START_MILLIS);
            mEndMillis = savedInstanceState.getLong(BUNDLE_KEY_END_MILLIS);
        }

        mView = inflater.inflate(R.layout.event_info, container, false);

        //?????? view???
        View backButton = mView.findViewById(R.id.back_button);
        mScrollView = mView.findViewById(R.id.event_info_scroll_view);
        mLoadingMsgView = mView.findViewById(R.id.event_info_loading_msg);
        mReminderTextView = mView.findViewById(R.id.reminder_text);

        if (mUri == null) {
            //Event ID????????? Uri??? ?????????.
            mUri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
        }

        //Fade Animation ??????
        mAnimateAlpha = ObjectAnimator.ofFloat(mScrollView, "Alpha", 0, 1);
        mAnimateAlpha.setDuration(FADE_IN_TIME);
        mAnimateAlpha.addListener(new AnimatorListenerAdapter() {
            int defLayerType;

            @Override
            public void onAnimationStart(Animator animation) {
                // Use hardware layer for better performance during animation
                defLayerType = mScrollView.getLayerType();
                mScrollView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                // Ensure that the loading message is gone before showing the
                // event info
                mLoadingMsgView.removeCallbacks(mLoadingMsgAlphaUpdater);
                mLoadingMsgView.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mScrollView.setLayerType(defLayerType, null);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mScrollView.setLayerType(defLayerType, null);
                // Do not cross fade after the first time
                mNoCrossFade = true;
            }
        });

        //???????????? ????????????, ??????????????? ????????? View?????? alpha?????? 0?????? ?????? ??????????????? ??????.
        mLoadingMsgView.setAlpha(0);
        mScrollView.setAlpha(0);

        //delay????????? ????????? ??????????????? ????????????.(?????????????????? ??? ???????????? ????????? ????????? ???????????? ??????)
        mLoadingMsgView.postDelayed(mLoadingMsgAlphaUpdater, LOADING_MSG_DELAY);

        //Query??? ?????? ?????? ???????????????
        mHandler.startQuery(TOKEN_QUERY_EVENT, null, mUri, EVENT_PROJECTION,
                null, null, null);

        //???????????????????????? ????????????.
        prepareReminders();

        //??????, ?????? ???????????? ??????????????? ??????
        View editButton = mView.findViewById(R.id.action_edit);
        View deleteButton = mView.findViewById(R.id.action_delete);
        editButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                doEdit();
            }
        });
        deleteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mDeleteDialogVisible)
                    return;

                mDeleteHelper =
                        new DeleteEventHelper(mActivity, mReminders, true);
                mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
                mDeleteDialogVisible = true;
                mDeleteHelper.delete(mStartMillis, mEndMillis, mEventId, -1, onDeleteRunnable);
            }
        });

        //`?????? ??????`????????? ???????????? parent activity??? onBackPressed??? ????????????.
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onBackPressed();
            }
        });

        Utils.addCommonTouchListener(backButton);
        Utils.addCommonTouchListener(editButton);
        Utils.addCommonTouchListener(deleteButton);

        return mView;
    }

    /**
     * ????????? ?????? Cursor??? ???????????????.
     * @return Cursor??? null????????? ???????????? true???, ????????? ????????? false??? ????????????.
     */
    private boolean initEventCursor() {
        if ((mEventCursor == null) || (mEventCursor.getCount() == 0)) {
            return true;
        }
        mEventCursor.moveToFirst();
        mEventId = mEventCursor.getInt(EVENT_INDEX_ID); //Id
        mHasAlarm = mEventCursor.getInt(EVENT_INDEX_HAS_ALARM) == 1;    //??????????????? ????????? ??????????
        mCalendarAllowedReminders = mEventCursor.getString(EVENT_INDEX_ALLOWED_REMINDERS); //???????????? ????????????
        return false;
    }

    /**
     * ????????? ??????
     * @param outState Bundle
     */
    @Override
    public void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);

        //????????? Id, ??????/????????????, ?????????????????? ?????????????????? ????????????.
        outState.putLong(BUNDLE_KEY_EVENT_ID, mEventId);
        outState.putLong(BUNDLE_KEY_START_MILLIS, mStartMillis);
        outState.putLong(BUNDLE_KEY_END_MILLIS, mEndMillis);
        outState.putBoolean(BUNDLE_KEY_DELETE_DIALOG_VISIBLE, mDeleteDialogVisible);
    }

    @Override
    public void onDestroy() {
        //Cursor??? ????????????
        if (mEventCursor != null) {
            mEventCursor.close();
        }
        if (mCalendarsCursor != null) {
            mCalendarsCursor.close();
        }
        super.onDestroy();
    }

    /**
     * ??????????????? ????????????
     */
    private void doEdit() {
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
        Intent intent = new Intent(Intent.ACTION_EDIT, uri);
        intent.setClass(mActivity, EditEventActivity.class);

        //?????????????????? extra???????????? ????????????.
        intent.putExtra(EXTRA_EVENT_BEGIN_TIME, mStartMillis);
        intent.putExtra(EXTRA_EVENT_END_TIME, mEndMillis);
        intent.putExtra(EXTRA_EVENT_ALL_DAY, mAllDay);
        intent.putExtra(EditEventActivity.EXTRA_EVENT_REMINDERS, mReminders);
        intent.putExtra(EVENT_EDIT_ON_LAUNCH, true);

        //EditEventActivity ??? ?????????.
        startActivity(intent);
    }

    /**
     * ????????? UI??? ??????
     * @param view
     */
    @SuppressLint("ClickableViewAccessibility")
    private void updateEvent(View view) {
        if (mEventCursor == null || view == null) {
            return;
        }

        //??????????????? ??????
        int eventTypeId = mEventCursor.getInt(EVENT_INDEX_EVENT_COLOR);
        EventTypeManager.OneEventType oneEventType = EventTypeManager.getEventTypeFromId(eventTypeId);
        ImageView imageView = mView.findViewById(R.id.image);
        int imageSize = (int) getResources().getDimension(R.dimen.event_item_image_size);

        //??????????????? ImageView??? ??????
        Resources resources = getResources();
        Drawable drawable = ResourcesCompat.getDrawable(resources, oneEventType.imageResource, null).mutate();
        drawable.setBounds(0,0, imageSize, imageSize);
        drawable.setTint(resources.getColor(oneEventType.color, null));
        imageView.setImageDrawable(drawable);

        //???????????? ??????
        String eventName = mEventCursor.getString(EVENT_INDEX_TITLE);
        if (eventName == null || eventName.length() == 0) {
            eventName = requireActivity().getString(R.string.no_title_label);
        }

        //?????????????????? ??????
        mAllDay = mEventCursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
        //?????? ??????
        String description = mEventCursor.getString(EVENT_INDEX_DESCRIPTION);

        //???????????? ??????
        String rRule = mEventCursor.getString(EVENT_INDEX_RRULE);

        //mStartMillis, mEndMillis??? ???????????? ???????????? db????????? ???????????????.
        if (mStartMillis == 0 && mEndMillis == 0) {
            mStartMillis = mEventCursor.getLong(EVENT_INDEX_DTSTART);
            mEndMillis = mEventCursor.getLong(EVENT_INDEX_DTEND);
            if (mEndMillis == 0) {
                String duration = mEventCursor.getString(EVENT_INDEX_DURATION);
                if (!TextUtils.isEmpty(duration)) {
                    try {
                        Duration d = new Duration();
                        d.parse(duration);
                        long endMillis = mStartMillis + d.getMillis();
                        if (endMillis >= mStartMillis) {
                            mEndMillis = endMillis;
                        } else {
                            Log.d(TAG, "Invalid duration string: " + duration);
                        }
                    } catch (DateException e) {
                        Log.d(TAG, "Error parsing duration string " + duration, e);
                    }
                }
                if (mEndMillis == 0) {
                    mEndMillis = mStartMillis;
                }
            }
        }

        //????????? ????????? ??????
        String localTimezone = Utils.getTimeZone(getContext(), mTZUpdater);

        //??????????????? ????????? ????????? ??????
        final DateTime startDateTime;
        if(mAllDay)
            startDateTime = new DateTime(DateTimeZone.UTC).withMillis(mStartMillis);
        else
            startDateTime = new DateTime(mStartMillis);
        String displayedDateTime = "";

        if(mAllDay){
            DateTime dateTime = new DateTime(startDateTime.getYear(), startDateTime.getMonthOfYear(), startDateTime.getDayOfMonth(), 0, 0);
            displayedDateTime = DateUtils.formatDateTime(getContext(), dateTime.getMillis(),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR);
        }
        else {
            displayedDateTime = DateUtils.formatDateTime(getContext(), startDateTime.getMillis(),
                            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR);
        }

        //?????? ??????
        setTextCommon(view, R.id.title, eventName);

        //???????????? ??????
        setTextCommon(view, R.id.when_datetime, displayedDateTime);

        //??????????????? ??????
        String repeatString = null;
        if (!TextUtils.isEmpty(rRule)) {
            EventRecurrence eventRecurrence = new EventRecurrence();
            eventRecurrence.parse(rRule);
            Time date = new Time(localTimezone);
            date.set(mStartMillis);
            if (mAllDay) {
                date.timezone = Time.TIMEZONE_UTC;
            }
            eventRecurrence.setStartDate(date);
            repeatString = Utils.getUpperString(Objects.requireNonNull(EventRecurrenceFormatter.getRepeatString(
                    getContext(), resources,
                    eventRecurrence, true)));
        }
        //?????????????????? ??????
        if (repeatString == null || repeatString.isEmpty()) {
            setTextCommon(view, R.id.when_repeat, getString(R.string.does_not_repeat));
        } else {
            setTextCommon(view, R.id.when_repeat, repeatString);
        }

        //`??????` ??????
        if (description != null && description.length() != 0) {
            setTextCommon(view, R.id.description, description);
        }
    }

    private void updateCalendar() {
        if (mCalendarsCursor != null && mEventCursor != null) {
            mCalendarsCursor.moveToFirst();

            //????????? ???????????? ?????? ?????? query??? ????????????.
            mHandler.startQuery(TOKEN_QUERY_VISIBLE_CALENDARS, null, Calendars.CONTENT_URI,
                    CALENDARS_PROJECTION, CALENDARS_VISIBLE_WHERE, new String[] {"1"}, null);
        } else {
            sendAccessibilityEventIfQueryDone(TOKEN_QUERY_DUPLICATE_CALENDARS);
        }
    }

    /**
     * Cursor????????? ???????????????????????? ???????????????.
     * @param cursor
     */
    public void initReminders(Cursor cursor) {
        //???????????????????????? ?????????
        mOriginalReminders.clear();
        mUnsupportedReminders.clear();

        String reminderText = "";

        while (cursor.moveToNext()) {
            int minutes = cursor.getInt(EditEventHelper.REMINDERS_INDEX_MINUTES);
            int method = cursor.getInt(EditEventHelper.REMINDERS_INDEX_METHOD);

            if (method != Reminders.METHOD_DEFAULT && !mReminderMethodValues.contains(method)) {
                //???????????? ?????? ????????????????????????
                mUnsupportedReminders.add(ReminderEntry.valueOf(minutes, method));
            } else {
                //???????????? ????????????????????????
                mOriginalReminders.add(ReminderEntry.valueOf(minutes, method));
            }

            int reminderIndex = mReminderMinuteValues.indexOf(minutes);
            if(reminderIndex >= 0)
                reminderText = mReminderMinuteLabels.get(reminderIndex);
        }

        //????????????????????? ????????????.
        Collections.sort(mOriginalReminders);

        //`????????????`label ??????
        if (mHasAlarm) {
            mReminders = mOriginalReminders;

            if(!reminderText.equals(""))
                mReminderTextView.setText(reminderText);
        }
    }

    /**
     * Id??? ????????? TextView??? Text??????
     * @param view ?????? View
     * @param id Id
     * @param text
     */
    private void setTextCommon(View view, int id, CharSequence text) {
        TextView textView = view.findViewById(id);
        if (textView == null)
            return;
        textView.setText(text);
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsPaused = false;
        if (mDismissOnResume) {
            mHandler.post(onDeleteRunnable);
        }
    }

    @Override
    public void onPause() {
        mIsPaused = true;
        mHandler.removeCallbacks(onDeleteRunnable);
        super.onPause();
    }

    @Override
    public void eventsChanged() {
    }

    @Override
    public void minuteChanged() {
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.EVENTS_CHANGED;
    }

    @Override
    public void handleEvent(EventInfo event) {
        Log.d("HandleEvent", "EnterInfoFragment");
    }

    /**
     * ?????? - `?????? ??? ?????? ??????` ?????? ????????? ??????????????????
     * @param eventId
     */
    public void onEventsChangedWithId(long eventId){
        if(eventId != -1) {
            mEventId = eventId;
            mUri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
        }
        mStartMillis = mEndMillis = 0;
        reloadEvents();
    }

    /**
     * ?????? - `????????? ??????` ?????? ????????? ??????????????????
     * @param eventId
     * @param start ????????????(?????????)
     * @param end ????????????(?????????)
     */
    public void onEventsChangedWithId(long eventId, long start, long end){
        if(eventId != -1) {
            mEventId = eventId;
            mUri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
        }
        mStartMillis = start;
        mEndMillis = end;
        reloadEvents();
    }

    /**
     * ????????? ?????? ??????
     */
    public void reloadEvents() {
        if (mHandler != null) {
            mHandler.startQuery(TOKEN_QUERY_EVENT, null, mUri, EVENT_PROJECTION,
                    null, null, null);
        }
    }

    /**
     * ???????????? Minute, Method Label/Value ?????? ?????????.
     * ????????? ???????????? ??????.
     */
    synchronized private void prepareReminders() {
        //?????? ????????? ?????? ???????????? ????????????????????? ????????? ?????????.
        if (mReminderMinuteValues != null && mReminderMinuteLabels != null
                && mReminderMethodValues != null && mReminderMethodLabels != null
                && mCalendarAllowedReminders == null) {
            return;
        }

        Resources r = getResources();
        mReminderMinuteValues = loadIntegerArray(r, R.array.reminder_minutes_values);   //??? ????????? ????????????
        mReminderMinuteLabels = loadStringArray(r, R.array.reminder_minutes_labels);    //??? ???????????? ????????????
        mReminderMethodValues = loadIntegerArray(r, R.array.reminder_methods_values);   //???????????? ????????? ????????????
        mReminderMethodLabels = loadStringArray(r, R.array.reminder_methods_labels);    //???????????? ???????????? ????????????

        //???????????? ?????? ???????????????????????? ?????????.
        if (mCalendarAllowedReminders != null) {
            EventViewUtils.reduceMethodList(mReminderMethodValues, mReminderMethodLabels,
                    mCalendarAllowedReminders);
        }
        if (mView != null) {
            mView.invalidate();
        }
    }

    /**
     * ?????????????????? ??????????????? ??????
     * @return Dialog.OnDismissListener
     */
    private Dialog.OnDismissListener createDeleteOnDismissListener() {
        return new Dialog.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        // Since OnPause will force the dialog to dismiss , do
                        // not change the dialog status
                        if (!mIsPaused) {
                            mDeleteDialogVisible = false;
                        }
                    }
                };
    }

    /**
     * ?????? Id
     * @return
     */
    public long getEventId() {
        return mEventId;
    }

    /**
     * ????????????(?????????)
     * @return
     */
    public long getStartMillis() {
        return mStartMillis;
    }

    /**
     * ????????????(?????????)
     * @return
     */
    public long getEndMillis() {
        return mEndMillis;
    }

    /**
     * Calendar, Event, Reminder?????? ?????? ?????? Query???????????????
     */
    private class QueryHandler extends AsyncQueryService {
        public QueryHandler(Context context) {
            super(context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            //Cursor ??? null??? ??????????????? activity??? ????????????????????? ????????? ?????????.
            final Activity activity = getActivity();
            if (activity == null || activity.isFinishing()) {
                if (cursor != null) {
                    cursor.close();
                }
                return;
            }

            switch (token) {
                case TOKEN_QUERY_EVENT:
                    mEventCursor = Utils.matrixCursorFromCursor(cursor);
                    if (initEventCursor()) {
                        //Cursor??? ????????????
                        //????????? ????????? ?????? ?????????????????? ??????????????? activity??? ????????????.
                        activity.finish();
                        return;
                    }

                    updateEvent(mView);
                    prepareReminders();

                    //Calendar?????? ?????? ?????? query??????
                    Uri uri = Calendars.CONTENT_URI;
                    String[] args = new String[]{
                            Long.toString(mEventCursor.getLong(EVENT_INDEX_CALENDAR_ID))};
                    startQuery(TOKEN_QUERY_CALENDARS, null, uri, CALENDARS_PROJECTION,
                            CALENDARS_WHERE, args, null);
                    break;
                case TOKEN_QUERY_CALENDARS:
                    mCalendarsCursor = Utils.matrixCursorFromCursor(cursor);
                    updateCalendar();
                    uri = Colors.CONTENT_URI;
                    startQuery(TOKEN_QUERY_COLORS, null, uri, null, null, null,
                            null);

                    if (mHasAlarm) {
                        //?????????????????? ?????? ?????? query??????
                        args = new String[]{Long.toString(mEventId)};
                        uri = Reminders.CONTENT_URI;
                        startQuery(TOKEN_QUERY_REMINDERS, null, uri,
                                REMINDERS_PROJECTION, REMINDERS_WHERE, args, null);
                    } else {
                        sendAccessibilityEventIfQueryDone(TOKEN_QUERY_REMINDERS);
                    }
                    break;
                case TOKEN_QUERY_COLORS:
                    cursor.close();
                    break;
                case TOKEN_QUERY_REMINDERS:
                    mRemindersCursor = Utils.matrixCursorFromCursor(cursor);
                    initReminders(mRemindersCursor);
                    break;
                case TOKEN_QUERY_VISIBLE_CALENDARS:
                    if (cursor.getCount() > 1) {
                        //Calendar????????? ???????????? ?????? query??????
                        String displayName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
                        mHandler.startQuery(TOKEN_QUERY_DUPLICATE_CALENDARS, null,
                                Calendars.CONTENT_URI, CALENDARS_PROJECTION,
                                CALENDARS_DUPLICATE_NAME_WHERE, new String[]{displayName}, null);
                    } else {
                        mCurrentQuery |= TOKEN_QUERY_DUPLICATE_CALENDARS;
                    }
                    break;
                case TOKEN_QUERY_DUPLICATE_CALENDARS:
                    SpannableStringBuilder sb = new SpannableStringBuilder();

                    //?????? ????????? ?????????.
                    String calendarName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
                    sb.append(calendarName);

                    //??????????????? email????????? ?????????.
                    String email = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
                    if (cursor.getCount() > 1 && !calendarName.equalsIgnoreCase(email) &&
                            Utils.isValidEmail(email)) {
                        sb.append(" (").append(email).append(")");
                    }
                    break;
            }
            cursor.close();
            sendAccessibilityEventIfQueryDone(token);

            //?????? Query?????? ????????? ???????????? ?????????????????? ????????????.
            if (mCurrentQuery == TOKEN_QUERY_ALL) {
                //??????????????? ???????????? ?????????
                if (mLoadingMsgView.getAlpha() == 1) {
                    //??????????????? animation??? ?????????.
                    long timeDiff = LOADING_MSG_MIN_DISPLAY_TIME - (System.currentTimeMillis() -
                            mLoadingMsgStartTime);
                    if (timeDiff > 0) {
                        mAnimateAlpha.setStartDelay(timeDiff);
                    }
                }

                //Fade Animation??? ?????????????????????(Query????????? ?????? ?????? ?????????)
                if (!mAnimateAlpha.isRunning() && !mAnimateAlpha.isStarted() && !mNoCrossFade) {
                    mAnimateAlpha.start();
                }

                //Fade Animation??? ????????????
                else {
                    //??????????????? ????????? ?????? ????????????????????? ????????????.
                    mScrollView.setAlpha(1);
                    mLoadingMsgView.setVisibility(View.GONE);
                }

                if (mDeleteDialogVisible) {
                    //??????????????? ?????????
                    mDeleteHelper = new DeleteEventHelper(mActivity, true);
                    mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
                    mDeleteHelper.delete(mStartMillis, mEndMillis, mEventId, -1, onDeleteRunnable);
                }
            }
        }
    }
}
