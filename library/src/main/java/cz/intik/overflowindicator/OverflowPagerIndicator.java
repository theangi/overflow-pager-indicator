package cz.intik.overflowindicator;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.Nullable;
import android.support.v7.widget.PagerSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;

import com.transitionseverywhere.ChangeBounds;
import com.transitionseverywhere.Fade;
import com.transitionseverywhere.Transition;
import com.transitionseverywhere.TransitionManager;
import com.transitionseverywhere.TransitionSet;

import java.util.Arrays;

/**
 * Pager indicator widget
 * <p>
 * - attach to recyclerView with {@link #attachToRecyclerView(RecyclerView)}
 * - add page selecting behavior with {@link SimpleSnapHelper} or custom {@link PagerSnapHelper}
 * or with custom logic which calls {@link #onPageSelected(int)}
 *
 * @author Petr Introvic <introvic.petr@gmail.com>
 *         created 07.06.2017.
 */
public class OverflowPagerIndicator extends LinearLayout {
	private static final int DEFAULT_MAX_INDICATORS = 9;
	private static final int DEFAULT_INDICATOR_SIZE = 12;
	private static final int DEFAULT_INDICATOR_MARGIN = 2;
	private static final int DEFAULT_STROKE_WIDTH = 3;

	// State also represents indicator scale factor
	private static final float STATE_GONE     = 0;
	private static final float STATE_SMALLEST = 0.2f;
	private static final float STATE_SMALL    = 0.4f;
	private static final float STATE_NORMAL   = 0.6f;
	private static final float STATE_SELECTED = 1.0f;

	private int mIndicatorCount;
	private int mLastSelected;
	private int mIndicatorSize;
	private int mIndicatorMargin;

	private RecyclerView         mRecyclerView;
	private OverflowDataObserver mDataObserver;

	private Integer mDotsMaxCount;
	private Integer mDotsSize;
	private Integer mDotsMargin;

	private String mDotsFillColor;
	private String mDotsStrokeColor;
	private Integer mDotsStrokePixelWidth;

	public OverflowPagerIndicator(Context context, AttributeSet attrs) {
		super(context, attrs);

		TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.OverflowPagerIndicator, 0, 0);

		mDotsMaxCount = a.getInteger(R.styleable.OverflowPagerIndicator_dots_max_count, DEFAULT_MAX_INDICATORS);
		mDotsSize = a.getDimensionPixelSize(R.styleable.OverflowPagerIndicator_dots_size, DEFAULT_INDICATOR_SIZE);
		mDotsMargin = a.getDimensionPixelSize(R.styleable.OverflowPagerIndicator_dots_margin, DEFAULT_INDICATOR_MARGIN);

		mDotsFillColor = a.getString(R.styleable.OverflowPagerIndicator_dots_fill_color);
		mDotsStrokeColor = a.getString(R.styleable.OverflowPagerIndicator_dots_stroke_color);
		mDotsStrokePixelWidth = a.getDimensionPixelSize(R.styleable.OverflowPagerIndicator_dots_stroke_width, DEFAULT_STROKE_WIDTH);

		if (mDotsFillColor == null){
			mDotsFillColor = context.getString(R.string.default_fill_color);
		}

		if(mDotsStrokeColor == null){
			mDotsStrokeColor = context.getString(R.string.default_stroke_color);
		}

		DisplayMetrics dm = getResources().getDisplayMetrics();
		mIndicatorSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mDotsSize, dm);
		mIndicatorMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mDotsMargin, dm);

		mDataObserver = new OverflowDataObserver(this);
	}

	@Override
	protected void onDetachedFromWindow() {
		if (mRecyclerView != null) {
			try {
				mRecyclerView.getAdapter().unregisterAdapterDataObserver(mDataObserver);
			} catch (IllegalStateException ise) {
				// Do nothing
			}
		}

		super.onDetachedFromWindow();
	}

	/**
	 * @param position Page to be selected
	 */
	public void onPageSelected(int position) {
		if (mIndicatorCount > mDotsMaxCount) {
			updateOverflowState(position);
		} else {
			updateSimpleState(position);
		}
	}

	/**
	 * @param recyclerView Target recycler view
	 */
	public void attachToRecyclerView(final RecyclerView recyclerView) {
		mRecyclerView = recyclerView;
		mRecyclerView.getAdapter().registerAdapterDataObserver(mDataObserver);

		initIndicators();
	}

	void updateIndicatorsCount() {
		if (mIndicatorCount != mRecyclerView.getAdapter().getItemCount()) {
			initIndicators();
		}
	}

	private void initIndicators() {
		mLastSelected = -1;
		mIndicatorCount = mRecyclerView.getAdapter().getItemCount();
		createIndicators(mIndicatorSize, mIndicatorMargin);
		onPageSelected(0);
	}

	private void updateSimpleState(int position) {
		if (mLastSelected != -1) {
			animateViewScale(getChildAt(mLastSelected), STATE_NORMAL);
		}

		animateViewScale(getChildAt(position), STATE_SELECTED);

		mLastSelected = position;
	}

	private void updateOverflowState(int position) {
		if (mIndicatorCount == 0) {
			return;
		}

		if(position < 0 || position > mIndicatorCount){
			return;
		}

		Transition transition = new TransitionSet()
				.setOrdering(TransitionSet.ORDERING_TOGETHER)
				.addTransition(new ChangeBounds())
				.addTransition(new Fade());

		TransitionManager.beginDelayedTransition(this, transition);

		float[] positionStates = new float[mIndicatorCount + 1];
		Arrays.fill(positionStates, STATE_GONE);

		int start     = position - mDotsMaxCount + 4;
		int realStart = Math.max(0, start);

		if (realStart + mDotsMaxCount > mIndicatorCount) {
			realStart = mIndicatorCount - mDotsMaxCount;
			positionStates[mIndicatorCount - 1] = STATE_NORMAL;
			positionStates[mIndicatorCount - 2] = STATE_NORMAL;
		} else {
			if (realStart + mDotsMaxCount - 2 < mIndicatorCount) {
				positionStates[realStart + mDotsMaxCount - 2] = STATE_SMALL;
			}
			if (realStart + mDotsMaxCount - 1 < mIndicatorCount) {
				positionStates[realStart + mDotsMaxCount - 1] = STATE_SMALLEST;
			}
		}

		for (int i = realStart; i < realStart + mDotsMaxCount - 2; i++) {
			positionStates[i] = STATE_NORMAL;
		}

		if (position > 5) {
			positionStates[realStart] = STATE_SMALLEST;
			positionStates[realStart + 1] = STATE_SMALL;
		} else if (position == 5) {
			positionStates[realStart] = STATE_SMALL;
		}

		positionStates[position] = STATE_SELECTED;

		updateIndicators(positionStates);

		mLastSelected = position;
	}

	private void updateIndicators(float[] positionStates) {
		for (int i = 0; i < mIndicatorCount; i++) {
			View  v     = getChildAt(i);
			float state = positionStates[i];

			if (state == STATE_GONE) {
				v.setVisibility(GONE);

			} else {
				v.setVisibility(VISIBLE);
				animateViewScale(v, state);
			}

		}
	}

	private void createIndicators(int indicatorSize, int margin) {
		removeAllViews();

		if (mIndicatorCount <= 1) {
			return;
		}

		for (int i = 0; i < mIndicatorCount; i++) {
			addIndicator(mIndicatorCount > mDotsMaxCount, indicatorSize, margin);
		}
	}

	private void addIndicator(boolean isOverflowState, int indicatorSize, int margin) {
		View view = new View(getContext());

		int fillColor = Color.parseColor(mDotsFillColor);
		int strokeColor = Color.parseColor(mDotsStrokeColor);

		GradientDrawable dotDrawable = new GradientDrawable();
		dotDrawable.setColor(fillColor);
		dotDrawable.setShape(GradientDrawable.OVAL);
		dotDrawable.setStroke(mDotsStrokePixelWidth, strokeColor);

		view.setBackground(dotDrawable);

		if (isOverflowState) {
			animateViewScale(view, STATE_SMALLEST);
		} else {
			animateViewScale(view, STATE_NORMAL);
		}

		MarginLayoutParams params = new MarginLayoutParams(indicatorSize, indicatorSize);
		params.leftMargin = margin;
		params.rightMargin = margin;

		addView(view, params);
	}

	private void animateViewScale(@Nullable View view, float scale) {
		if (view == null) {
			return;
		}

		view.animate()
				.scaleX(scale)
				.scaleY(scale);
	}

}
