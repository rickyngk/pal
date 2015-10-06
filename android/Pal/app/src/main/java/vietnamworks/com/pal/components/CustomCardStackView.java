package vietnamworks.com.pal.components;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import vietnamworks.com.pal.R;

/**
 * Created by duynk on 10/6/15.
 */
public class CustomCardStackView extends FrameLayout {
    CustomCardView front, mid, back;
    FrameLayout holder;
    FrameLayout.LayoutParams frontLayout;
    FrameLayout.LayoutParams backLayout;
    FrameLayout.LayoutParams midLayout;
    private boolean isLocked = false;

    private int itemIndex = 0;
    private EventCallback callback;

    Thread thread;
    private boolean isAlive = true;
    private android.os.Handler handler = new android.os.Handler();

    public CustomCardStackView(Context context) {
        super(context);
        initializeViews(context);
    }

    public CustomCardStackView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeViews(context);
    }

    public CustomCardStackView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initializeViews(context);
    }

    public void initializeViews(Context context) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.custom_card_stack, this);
    }

    public void Lock() {this.isLocked = true;}
    public void Unlock() {this.isLocked = false;}

    public void setCallback() {
        this.callback = callback;
    }

    @Override
    protected void onFinishInflate() {
        float d = this.getResources().getDisplayMetrics().density;

        super.onFinishInflate();
        front = (CustomCardView) this.findViewById(R.id.ccs_front);
        mid = (CustomCardView) this.findViewById(R.id.ccs_mid);
        back = (CustomCardView) this.findViewById(R.id.ccs_back);
        holder = (FrameLayout) this.findViewById(R.id.ccs_holder);

        front.setHolderRef(this);

        thread = new Thread() {
            @Override
            public void run() {
                try {
                    while(isAlive) {
                        update();
                        sleep(40);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        itemIndex = 0;
        switchState(STATE_PRE_INIT);

        thread.start();
    }

    @Override protected void onDetachedFromWindow() {
        isAlive = false;
        super.onDetachedFromWindow();
    }

    public final static int STATE_NONE = 0;
    public final static int STATE_PRE_INIT = STATE_NONE + 1;
    public final static int STATE_IDLE = STATE_PRE_INIT + 1;
    public final static int STATE_DRAG = STATE_IDLE + 1;
    public final static int STATE_DRAG_OUT = STATE_DRAG + 1;
    public final static int STATE_SCROLL_BACK = STATE_DRAG_OUT + 1;
    public final static int STATE_REORDER = STATE_SCROLL_BACK + 1;
    public final static int STATE_FLY_IN = STATE_REORDER + 1;

    public final String[] STATE_NAME = {"none", "pre", "idle", "drag", "drag-out", "scroll back", "reorder", "fly-in"};

    public final static float CARD_MARGIN = 30f;
    public final static float CARD_MARGIN_DELTA = 3f;
    public final static float CARD_SCALE_STEP = 0.1f;

    private float mDownX;
    private int originLayoutMargin;
    private int state = STATE_NONE;
    private int nextState = STATE_NONE;
    private int lastState = STATE_NONE;
    private int targetScrollX;

    private void switchState(int state) {nextState = state;}

    public void onChildTouchEvent(CustomCardView child, MotionEvent ev) {
        if (!isLocked && (state == STATE_IDLE || state == STATE_DRAG || state == STATE_DRAG_OUT)) {
            final int action = MotionEventCompat.getActionMasked(ev);
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                if (state == STATE_DRAG_OUT) {
                    switchState(STATE_REORDER);
                } else {
                    switchState(STATE_SCROLL_BACK);
                }
            }
            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    mDownX = ev.getRawX();
                    originLayoutMargin = frontLayout.leftMargin;
                    if (state != STATE_DRAG && state != STATE_DRAG_OUT) {
                        switchState(STATE_DRAG);
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    targetScrollX = originLayoutMargin + (int) (ev.getRawX() - mDownX);
                    break;
                }
            }
        }
    }

    private void initLayout() {
        final float density = this.getResources().getDisplayMetrics().density;
        frontLayout = (FrameLayout.LayoutParams)front.getLayoutParams();
        frontLayout.setMargins(0, (int) (2*CARD_MARGIN*density), 0, 0);
        front.setScaleX(1.0f);
        front.setScaleY(1.0f);
        front.setLayoutParams(frontLayout);
        front.setBackgroundColor(getResources().getColor(R.color.icons));

        midLayout = (FrameLayout.LayoutParams)mid.getLayoutParams();
        midLayout.setMargins(0, (int) (CARD_MARGIN*density), 0, 0);
        mid.setScaleX(1.0f - CARD_SCALE_STEP);
        mid.setScaleY(1.0f - CARD_SCALE_STEP);
        mid.setLayoutParams(midLayout);

        backLayout = (FrameLayout.LayoutParams)back.getLayoutParams();
        backLayout.setMargins(backLayout.leftMargin, 0, 0, 0);
        back.setScaleX(1.0f - CARD_SCALE_STEP * 2.0f);
        back.setScaleY(1.0f - CARD_SCALE_STEP * 2.0f);
        back.setLayoutParams(backLayout);
    }


    private void update() {
        if (state != nextState) {
            System.out.println("CHANGE TO STATE ... " + STATE_NAME[nextState]);
            switch (nextState) {
                case STATE_SCROLL_BACK:
                    targetScrollX = 0;
                    break;
                case STATE_FLY_IN:
                    backLayout.leftMargin = targetScrollX;
                    targetScrollX = 0;
                    break;
            }

            lastState = state;
            state = nextState;
        }

        boolean requiredUpdateLayout = false;
        float movingPercent = 0f;
        switch (state) {
            case STATE_PRE_INIT:
                requiredUpdateLayout = true;
                break;
            case STATE_DRAG:
            case STATE_SCROLL_BACK:
            case STATE_DRAG_OUT:
                if (Math.abs(frontLayout.leftMargin - targetScrollX) < 10) {
                    frontLayout.leftMargin = targetScrollX;
                    requiredUpdateLayout = true;
                } else if (frontLayout.leftMargin != targetScrollX) {
                    frontLayout.leftMargin = lerp(frontLayout.leftMargin, targetScrollX, 0.5f);
                    requiredUpdateLayout = true;
                }
                if (requiredUpdateLayout) {
                    movingPercent = Math.min(Math.abs(frontLayout.leftMargin) / (front.getWidth()*0.5f), 1.0f);
                    if (movingPercent >= 1.0f) {
                        switchState(STATE_DRAG_OUT);
                    } else if (movingPercent > 0 && state == STATE_DRAG_OUT) {
                        switchState(STATE_DRAG);
                    }
                }

                if (frontLayout.leftMargin == 0 && state == STATE_SCROLL_BACK) {
                    switchState(STATE_IDLE);
                }
                break;
            case STATE_FLY_IN:
                if (Math.abs(backLayout.leftMargin - targetScrollX) < 10) {
                    backLayout.leftMargin = targetScrollX;
                    requiredUpdateLayout = true;
                } else if (backLayout.leftMargin != targetScrollX) {
                    backLayout.leftMargin = lerp(backLayout.leftMargin, targetScrollX, 0.5f);
                    requiredUpdateLayout = true;
                }
                if (targetScrollX == backLayout.leftMargin) {
                    switchState(STATE_IDLE);
                }
                break;
            case STATE_REORDER:
                requiredUpdateLayout = true;
                break;
            default:
                break;
        }

        final float movingScale = movingPercent;
        final boolean reorder = state == STATE_REORDER;
        final int _state = this.state;

        if (requiredUpdateLayout) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (_state == STATE_PRE_INIT) {
                        initLayout();
                        switchState(STATE_IDLE);
                        return;
                    }
                    if (!reorder) {
                        final float density = getResources().getDisplayMetrics().density;
                        front.setLayoutParams(frontLayout);
                        if (movingScale >= 1.0f) {
                            front.setBackgroundColor(getResources().getColor(android.support.design.R.color.material_grey_300));
                        } else {
                            front.resetBackgroundColor();
                        }
                        float mid_scalingFactor = (1.0f - CARD_SCALE_STEP) + CARD_SCALE_STEP * movingScale;
                        mid.setScaleX(mid_scalingFactor);
                        mid.setScaleY(mid_scalingFactor);
                        midLayout.setMargins(0, (int)((CARD_MARGIN + (CARD_MARGIN - CARD_MARGIN_DELTA) * movingScale)*density), 0, 0);
                        mid.setLayoutParams(midLayout);

                        float back_scalingFactor = (1.0f - CARD_SCALE_STEP*2.0f) + CARD_SCALE_STEP * movingScale;
                        back.setScaleX(back_scalingFactor);
                        back.setScaleY(back_scalingFactor);
                        backLayout.setMargins(backLayout.leftMargin, (int)(((CARD_MARGIN - CARD_MARGIN_DELTA) * movingScale)*density), 0, 0);
                        back.setLayoutParams(backLayout);
                    } else {
                        holder.removeAllViews();
                        holder.addView(front);
                        holder.addView(back);
                        holder.addView(mid);

                        front.resetBackgroundColor();
                        mid.setHolderRef(front.getHolderRef());
                        front.setHolderRef(null);

                        CustomCardView tmp = front;
                        front = mid;
                        mid = back;
                        back = tmp;

                        initLayout();
                        switchState(STATE_FLY_IN);
                    }
                }
            });
        }
    }

    int lerp(int start, int end, float percent)
    {
        return (int)(start + percent*(end - start));
    }

    public class EventCallback {
        public void onLaunched() {

        }
        public void onActive(int index, CustomCardView obj) {

        }
        public void onPrepare(int index, CustomCardView obj) {

        }
    }

}