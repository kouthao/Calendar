package com.android.calendar.kr.big;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.calendar.utils.Utils;
import com.android.calendar.event.EventManager;
import com.android.krcalendar.R;

import org.joda.time.DateTime;

import java.util.List;

import me.everything.android.ui.overscroll.VerticalOverScrollBounceEffectDecorator;
import me.everything.android.ui.overscroll.adapters.RecyclerViewOverScrollDecorAdapter;

import static com.android.calendar.utils.Utils.CUSTOM_TOUCH_DRAG_MOVE_RATIO_FWD;
import static me.everything.android.ui.overscroll.OverScrollBounceEffectDecoratorBase.DEFAULT_DECELERATE_FACTOR;
import static me.everything.android.ui.overscroll.OverScrollBounceEffectDecoratorBase.DEFAULT_TOUCH_DRAG_MOVE_RATIO_BCK;

/**
 * 양식 3
 * 한개 날자에 해당한 일정부분의 view
 * 날자, 일정목록 recyclerview를 가지고 있다.
 */
public class BigDayViewContainer extends LinearLayout {
    BigCalendarView.BigCalendarViewDelegate mDelegate;

    //자식 View 들
    TextView mDayLabel;
    View mNoEventView;
    RecyclerView mEventListView;
    BigDayEventListAdapter mAdapter;

    public BigDayViewContainer(Context context) {
        this(context, null);
    }

    public BigDayViewContainer(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BigDayViewContainer(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        mDayLabel = findViewById(R.id.day_label_view);
        mNoEventView = findViewById(R.id.no_event_view);
        mEventListView = findViewById(R.id.event_list_view);
    }

    public void setup(BigCalendarView.BigCalendarViewDelegate delegate, int year, int month, int day) {
        mDelegate = delegate;

        //날자,요일 label 설정(2021.1.1 금요일)
        DateTime dateTime = new DateTime(year, month, day, 0, 0);
        String dayString = year + "." + month + "." + day + " " + Utils.getWeekDayString(getContext(), dateTime.getDayOfWeek(), false);
        mDayLabel.setTextSize(mDelegate.getDayLabelSize());
        mDayLabel.setTextColor(mDelegate.getDayLabelColor());
        mDayLabel.setText(dayString);

        //일정목록얻기
        List<EventManager.OneEvent> eventList = EventManager.getEvents(getContext(), dateTime.getMillis(), EventManager.DAY);

        //일정이 없을때
        if(eventList.isEmpty()) {
            mNoEventView.setVisibility(VISIBLE);
        }

        //일정이 있을때
        else {
            mNoEventView.setVisibility(GONE);
        }

        //Adapter, LayoutManager 설정
        mAdapter = new BigDayEventListAdapter(year, month, day, getContext(), mDelegate, eventList);
        mEventListView.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        mEventListView.setAdapter(mAdapter);

        //Scroll bounce 효과를 준다.
        new VerticalOverScrollBounceEffectDecorator(new RecyclerViewOverScrollDecorAdapter(mEventListView),
                CUSTOM_TOUCH_DRAG_MOVE_RATIO_FWD, DEFAULT_TOUCH_DRAG_MOVE_RATIO_BCK, DEFAULT_DECELERATE_FACTOR);
    }
}
