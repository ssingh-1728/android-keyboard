package com.ghostman.keyboard;

import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodSubtype;

public class LatinKeyboardView extends KeyboardView {

    static final int KEYCODE_OPTIONS = -100;
    // TODO: Move this into android.inputmethodservice.Keyboard
    static final int KEYCODE_LANGUAGE_SWITCH = -101;

    public LatinKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LatinKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected boolean onLongPress(android.inputmethodservice.Keyboard.Key key) {
        if (key.codes[0] == Keyboard.KEYCODE_CANCEL) {
            getOnKeyboardActionListener().onKey(KEYCODE_OPTIONS, null);
            return true;
        } else {
            return super.onLongPress(key);
        }
    }

    void setShiftKeyIcon(final InputMethodSubtype subtype, Boolean state) {
        final LatinKeyboard keyboard= (LatinKeyboard)getKeyboard();
        if(state)
            keyboard.setShiftIcon(getResources().getDrawable(R.drawable.ic_keyboard_capslock_24px));
        else
            keyboard.setShiftIcon(getResources().getDrawable(R.drawable.ic_keyboard_arrow_up_24px));
        invalidateAllKeys();
    }

}
