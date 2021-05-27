package com.example.interviewtask_inspire.pinView

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.example.interviewtask_inspire.R

class PinEditText: AppCompatEditText {

    private val XML_NAMESPACE_ANDROID = "http://schemas.android.com/apk/res/android"

    val DEFAULT_MASK = "\u25CF"

    private var mMask: String? = null
    private var mMaskChars: StringBuilder? = null
    private var mSingleCharHint: String? = null
    private var mAnimatedType = 0
    private var mSpace = 24f //24 dp by default, space between the lines
    private var mCharSize: Float = 0.toFloat()
    private var mNumChars = 4f
    private var mTextBottomPadding = 8f //8dp by default, height of the text from our lines
    private var mMaxLength = 4
    private var mLineCoords: Array<RectF>?=null
    private lateinit var mCharBottom: FloatArray
    private var mCharPaint: Paint?=null
    private var mLastCharPaint: Paint?=null
    private var mSingleCharPaint: Paint?=null
    private var mPinBackground: Drawable? = null
    private var mTextHeight = Rect()
    private var mIsDigitSquare = false

    private var mClickListener: OnClickListener? = null
    private var mOnPinEnteredListener: OnPinEnteredListener? = null

    private var mLineStroke = 1f //1dp by default
    private var mLineStrokeSelected = 2f //2dp by default
    private var mLinesPaint: Paint?=null
    private var mAnimate = false
    private var mHasError = false
    private var mOriginalTextColors: ColorStateList? = null
    private var mStates = arrayOf(
        intArrayOf(android.R.attr.state_selected), // selected
        intArrayOf(android.R.attr.state_active), // error
        intArrayOf(android.R.attr.state_focused), // focused
        intArrayOf(-android.R.attr.state_focused) // unfocused
    )

    private var mColors = intArrayOf(Color.GREEN, Color.RED, Color.BLACK, Color.GRAY)

    private var mColorStates = ColorStateList(mStates, mColors)

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(context, attrs)
    }

    fun setMaxLength(maxLength: Int) {
        mMaxLength = maxLength
        mNumChars = maxLength.toFloat()

        filters = arrayOf<InputFilter>(InputFilter.LengthFilter(maxLength))

        text = null
        invalidate()
    }

    fun setMask(mask: String?) {
        mMask = mask
        mMaskChars = null
        invalidate()
    }

    fun setSingleCharHint(hint: String) {
        mSingleCharHint = hint
        invalidate()
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        val multi = context.resources.displayMetrics.density
        mLineStroke *= multi
        mLineStrokeSelected *= multi
        mSpace *= multi //convert to pixels for our density
        mTextBottomPadding *= multi //convert to pixels for our density

        val ta = context.obtainStyledAttributes(attrs, R.styleable.PinEditText, 0, 0)
        try {
            val outValue = TypedValue()
            ta.getValue(R.styleable.PinEditText_pinAnimationType, outValue)
            mAnimatedType = outValue.data
            mMask = ta.getString(R.styleable.PinEditText_pinCharacterMask)
            mSingleCharHint = ta.getString(R.styleable.PinEditText_pinRepeatedHint)
            mLineStroke = ta.getDimension(R.styleable.PinEditText_pinLineStroke, mLineStroke)
            mLineStrokeSelected =
                ta.getDimension(R.styleable.PinEditText_pinLineStrokeSelected, mLineStrokeSelected)
            mSpace = ta.getDimension(R.styleable.PinEditText_pinCharacterSpacing, mSpace)
            mTextBottomPadding = ta.getDimension(R.styleable.PinEditText_pinTextBottomPadding, mTextBottomPadding)
            mIsDigitSquare = ta.getBoolean(R.styleable.PinEditText_pinBackgroundIsSquare, mIsDigitSquare)
            mPinBackground = ta.getDrawable(R.styleable.PinEditText_pinBackgroundDrawable)
            val colors = ta.getColorStateList(R.styleable.PinEditText_pinLineColors)
            if (colors != null) {
                mColorStates = colors
            }
        } finally {
            ta.recycle()
        }

        mCharPaint = Paint(paint)
        mLastCharPaint = Paint(paint)
        mSingleCharPaint = Paint(paint)
        mLinesPaint = Paint(paint)
        mLinesPaint?.strokeWidth = mLineStroke

        val outValue = TypedValue()
        context.theme.resolveAttribute(
            R.attr.colorControlActivated,
            outValue, true
        )
        val colorSelected = outValue.data
        mColors[0] = colorSelected

        val colorFocused = if (isInEditMode) Color.GRAY else ContextCompat.getColor(context, R.color.pin_normal)
        mColors[1] = colorFocused

        val colorUnfocused = if (isInEditMode) Color.GRAY else ContextCompat.getColor(context, R.color.pin_normal)
        mColors[2] = colorUnfocused

        setBackgroundResource(0)

        mMaxLength = attrs?.getAttributeIntValue(XML_NAMESPACE_ANDROID, "maxLength", 4)?:0
        mNumChars = mMaxLength.toFloat()

        //Disable copy paste
        super.setCustomSelectionActionModeCallback(object : ActionMode.Callback {
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {}

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return false
            }
        })
        // When tapped, move cursor to end of text.
        super.setOnClickListener { v ->
            setSelection(text!!.length)
            if (mClickListener != null) {
                mClickListener?.onClick(v)
            }
        }

        super.setOnLongClickListener {
            setSelection(text!!.length)
            true
        }

        //If input type is password and no mask is set, use a default mask
        if (inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == InputType.TYPE_TEXT_VARIATION_PASSWORD && TextUtils.isEmpty(
                mMask
            )
        ) {
            mMask = DEFAULT_MASK
        } else if (inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD == InputType.TYPE_NUMBER_VARIATION_PASSWORD && TextUtils.isEmpty(
                mMask
            )
        ) {
            mMask = DEFAULT_MASK
        }

        if (!TextUtils.isEmpty(mMask)) {
            mMaskChars = getMaskChars()
        }

        //Height of the characters, used if there is a background drawable
        paint.getTextBounds("|", 0, 1, mTextHeight)

        mAnimate = mAnimatedType > -1
    }


    private fun getFullText(): CharSequence? {
        return if (TextUtils.isEmpty(mMask)) {
            text
        } else {
            getMaskChars()
        }
    }

    private fun getMaskChars(): StringBuilder {
        if (mMaskChars == null) {
            mMaskChars = StringBuilder()
        }
        val textLength = text!!.length
        while (mMaskChars?.length != textLength) {
            if (mMaskChars?.length ?: 0 < textLength) {
                mMaskChars?.append(mMask)
            } else {
                mMaskChars?.deleteCharAt((mMaskChars?.length ?: 0) - 1)
            }
        }
        return mMaskChars!!
    }


    private fun getColorForState(vararg states: Int): Int {
        return mColorStates.getColorForState(states, Color.GRAY)
    }

    /**
     * @param hasTextOrIsNext Is the color for a character that has been typed or is
     * the next character to be typed?
     */
    private fun updateColorForLines(hasTextOrIsNext: Boolean) {
        mLinesPaint?.let {
            if (mHasError) {
                it.setColor(getColorForState(android.R.attr.state_active))
            } else if (isFocused) {
                it.strokeWidth = mLineStrokeSelected
                it.color = getColorForState(android.R.attr.state_focused)
                if (hasTextOrIsNext) {
                    it.setColor(getColorForState(android.R.attr.state_selected))
                }else{
                    //BUG work around
//                    Log.v(javaClass.simpleName,"Else Case updateColorForLines ")
                }
            } else {
                it.strokeWidth = mLineStroke
                mLinesPaint?.setColor(getColorForState(-android.R.attr.state_focused))
            }
        }
    }

    private fun updateDrawableState(hasText: Boolean, isNext: Boolean) {
        if (mHasError) {
            mPinBackground?.state = intArrayOf(android.R.attr.state_active)
        } else if (isFocused) {
            mPinBackground?.state = intArrayOf(android.R.attr.state_focused)
            if (isNext) {
                mPinBackground?.state = intArrayOf(android.R.attr.state_focused, android.R.attr.state_selected)
            } else if (hasText) {
                mPinBackground?.state = intArrayOf(android.R.attr.state_focused, android.R.attr.state_checked)
            }
        } else {
            if (hasText) {
                mPinBackground?.state = intArrayOf(-android.R.attr.state_focused, android.R.attr.state_checked)
            } else {
                mPinBackground?.state = intArrayOf(-android.R.attr.state_focused)
            }
        }
    }

    fun setError(hasError: Boolean) {
        mHasError = hasError
        invalidate()
    }

    fun isError(): Boolean {
        return mHasError
    }

    /**
     * Request focus on this PinEntryEditText
     */
    fun focus() {
        requestFocus()

        // Show keyboard
        val inputMethodManager = context
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(this, 0)
    }

    override fun setInputType(type: Int) {
        super.setInputType(type)

        if (type and InputType.TYPE_TEXT_VARIATION_PASSWORD == InputType.TYPE_TEXT_VARIATION_PASSWORD || type and InputType.TYPE_NUMBER_VARIATION_PASSWORD == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
            // If input type is password and no mask is set, use a default mask
            if (TextUtils.isEmpty(mMask)) {
                setMask(DEFAULT_MASK)
            }
        } else {
            // If input type is not password, remove mask
            setMask(null)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mOriginalTextColors = textColors
        mOriginalTextColors?.let {
            mLastCharPaint?.color = it.defaultColor
            mCharPaint?.color = it.defaultColor
            mSingleCharPaint?.setColor(currentHintTextColor)
        }
        val availableWidth = width - ViewCompat.getPaddingEnd(this) - ViewCompat.getPaddingStart(this)
        mCharSize = if (mSpace < 0) {
            availableWidth / (mNumChars * 2 - 1)
        } else {
            (availableWidth - mSpace * (mNumChars - 1)) / mNumChars
        }

        mLineCoords = Array(mNumChars.toInt()){ RectF() }

        mCharBottom = FloatArray(mNumChars.toInt())
        var startX: Int
        val bottom = height - paddingBottom
        val rtlFlag: Int
        val isLayoutRtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL
        if (isLayoutRtl) {
            rtlFlag = -1
            startX = (width.toFloat() - ViewCompat.getPaddingStart(this).toFloat() - mCharSize).toInt()
        } else {
            rtlFlag = 1
            startX = ViewCompat.getPaddingStart(this)
        }
        var i = 0
        while (i < mNumChars) {

            mLineCoords?.let {
                it[i] = RectF(startX.toFloat(), bottom.toFloat(), startX + mCharSize, bottom.toFloat())
                if (mPinBackground != null) {
                    if (mIsDigitSquare) {
                        it[i].top = paddingTop.toFloat()
                        it[i].right = startX + it[i].width()
                    } else {
                        it[i].top -= (-mTextHeight.height() + mTextBottomPadding * 2)
                    }
                }
                mCharBottom[i] = it[i].bottom - mTextBottomPadding
            }

            startX += if (mSpace < 0) {
                (rtlFlag.toFloat() * mCharSize * 2f).toInt()
            } else {
                (rtlFlag * (mCharSize + mSpace)).toInt()
            }
            i++
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (mIsDigitSquare) {
            val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
            val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
            val measuredWidth: Int
            val measuredHeight: Int
            // If we want a square or circle pin box, we might be able
            // to figure out the dimensions outselves
            // if width and height are set to wrap_content or match_parent
            when {
                widthMode == View.MeasureSpec.EXACTLY -> {
                    measuredWidth = View.MeasureSpec.getSize(widthMeasureSpec)
                    measuredHeight = ((measuredWidth - (mNumChars - 1 * mSpace)) / mNumChars).toInt()
                }
                heightMode == View.MeasureSpec.EXACTLY -> {
                    measuredHeight = View.MeasureSpec.getSize(heightMeasureSpec)
                    measuredWidth = (measuredHeight * mNumChars + (mSpace * mNumChars - 1)).toInt()
                }
                widthMode == View.MeasureSpec.AT_MOST -> {
                    measuredWidth = View.MeasureSpec.getSize(widthMeasureSpec)
                    measuredHeight = ((measuredWidth - (mNumChars - 1 * mSpace)) / mNumChars).toInt()
                }
                heightMode == View.MeasureSpec.AT_MOST -> {
                    measuredHeight = View.MeasureSpec.getSize(heightMeasureSpec)
                    measuredWidth = (measuredHeight * mNumChars + (mSpace * mNumChars - 1)).toInt()
                }
                else -> {
                    // Both unspecific
                    // Try for a width based on our minimum
                    measuredWidth = paddingLeft + paddingRight + suggestedMinimumWidth

                    // Whatever the width ends up being, ask for a height that would let the pie
                    // get as big as it can
                    measuredHeight = ((measuredWidth - (mNumChars - 1 * mSpace)) / mNumChars).toInt()
                }
            }

            setMeasuredDimension(
                View.resolveSizeAndState(measuredWidth, widthMeasureSpec, 1),
                View.resolveSizeAndState(measuredHeight, heightMeasureSpec, 0)
            )
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        mClickListener = l
    }

    override fun setCustomSelectionActionModeCallback(actionModeCallback: ActionMode.Callback?) {
        throw RuntimeException("setCustomSelectionActionModeCallback() not supported.")
    }

    override fun onDraw(canvas: Canvas) {
        //super.onDraw(canvas);
        val text = getFullText()
        val textLength = text?.length ?: 0
        val textWidths = FloatArray(textLength)
        paint.getTextWidths(text, 0, textLength, textWidths)

        var hintWidth = 0f

        mSingleCharHint?.let {
            val hintWidths = FloatArray(it.length)
            paint.getTextWidths(it, hintWidths)
            for (i in hintWidths) {
                hintWidth += i
            }
        }

        var i = 0
        while (i < mNumChars) {
            //If a background for the pin characters is specified, it should be behind the characters.
            mPinBackground?.let {
                updateDrawableState(i < textLength, i == textLength)

                mLineCoords?.let { lineCoords->
                    it.setBounds(
                        lineCoords[i].left.toInt(),
                        lineCoords[i].top.toInt(),
                        lineCoords[i].right.toInt(),
                        lineCoords[i].bottom.toInt()
                    )
                }
                it.draw(canvas)
            }
            val middle = (mLineCoords?.get(i)?.left?:0f) + mCharSize / 2
            if (textLength > i) {
                if (!mAnimate || i != textLength - 1) {
                    canvas.drawText(text ?: "", i, i + 1, middle - textWidths[i] / 2, mCharBottom[i], mCharPaint!!)
                } else {
                    canvas.drawText(text ?: "", i, i + 1, middle - textWidths[i] / 2, mCharBottom[i], mLastCharPaint!!)
                }
            } else if (mSingleCharHint != null) {
                canvas.drawText(mSingleCharHint!!, middle - hintWidth / 2, mCharBottom[i], mSingleCharPaint!!)
            }
            //The lines should be in front of the text (because that's how I want it).
            if (mPinBackground == null) {
                updateColorForLines(i <= textLength)
                mLineCoords?.let {
                    canvas.drawLine(
                        it[i].left,
                        it[i].top,
                        it[i].right,
                        it[i].bottom,
                        mLinesPaint!!
                    )
                }
            }
            i++
        }
    }

    override fun setTypeface(tf: Typeface?) {
        super.setTypeface(tf)
        setCustomTypeface(tf)
    }

    override fun setTypeface(tf: Typeface?, style: Int) {
        super.setTypeface(tf, style)
        setCustomTypeface(tf)
    }

    private fun setCustomTypeface(tf: Typeface?) {
        mCharPaint?.typeface = tf
        mLastCharPaint?.typeface = tf
        mSingleCharPaint?.typeface = tf
        mLinesPaint?.typeface = tf
    }

    fun setPinLineColors(colors: ColorStateList) {
        mColorStates = colors
        invalidate()
    }

    fun setPinBackground(pinBackground: Drawable) {
        mPinBackground = pinBackground
        invalidate()
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {

        setError(false)
        if (mLineCoords == null || !mAnimate) {
            if (mOnPinEnteredListener != null && text?.length == mMaxLength) {
                mOnPinEnteredListener?.onPinEntered(text)
            }
            return
        }

        if (mAnimatedType == -1) {
            invalidate()
            return
        }

        if (lengthAfter > lengthBefore) {
            if (mAnimatedType == 0) {
                animatePopIn()
            } else {
                animateBottomUp(text, start)
            }
        }
    }


    private fun animatePopIn() {
        val va = ValueAnimator.ofFloat(1F, paint.textSize)
        va.duration = 200
        va.interpolator = OvershootInterpolator()
        va.addUpdateListener { animation ->
            mLastCharPaint?.textSize = animation.animatedValue as Float
            this@PinEditText.invalidate()
        }
        if (text?.length == mMaxLength && mOnPinEnteredListener != null) {
            va.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    mOnPinEnteredListener?.onPinEntered(text)
                }

                override fun onAnimationCancel(animation: Animator) {}

                override fun onAnimationRepeat(animation: Animator) {}
            })
        }
        va.start()
    }


    private fun animateBottomUp(text: CharSequence?, start: Int) {
        mCharBottom[start] = (mLineCoords?.get(start)?.bottom?:0f) - mTextBottomPadding
        val animUp = ValueAnimator.ofFloat(mCharBottom[start] + paint.textSize, mCharBottom[start])
        animUp.duration = 300
        animUp.interpolator = OvershootInterpolator()
        animUp.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            mCharBottom[start] = value
            this@PinEditText.invalidate()
        }

        mLastCharPaint?.alpha = 255
        val animAlpha = ValueAnimator.ofInt(0, 255)
        animAlpha.duration = 300
        animAlpha.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            mLastCharPaint?.alpha = value
        }

        val set = AnimatorSet()
        if (text?.length == mMaxLength && mOnPinEnteredListener != null) {
            set.addListener(object : Animator.AnimatorListener {

                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    mOnPinEnteredListener?.onPinEntered(getText())
                }

                override fun onAnimationCancel(animation: Animator) {}

                override fun onAnimationRepeat(animation: Animator) {

                }
            })
        }
        set.playTogether(animUp, animAlpha)
        set.start()
    }

    fun setAnimateText(animate: Boolean) {
        mAnimate = animate
    }

    fun setOnPinEnteredListener(l: OnPinEnteredListener) {
        mOnPinEnteredListener = l
    }
}