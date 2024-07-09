package org.telegram.messenger.partisan.masked_passcode_screen.calculator;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.masked_passcode_screen.MaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_passcode_screen.PasscodeEnteredDelegate;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;

public class CalculatorPasscodeScreen implements MaskedPasscodeScreen {
    private PasscodeEnteredDelegate delegate;
    private Context context;

    private final int BUTTON_X_MARGIN = 28;
    private final int BUTTON_Y_MARGIN = 16;
    private final int BUTTON_SIZE = 80;

    private final int BUTTONS_ROW_COUNT = 5;
    private final int BUTTONS_COL_COUNT = 4;

    private FrameLayout backgroundFrameLayout;
    private String inputString = "";
    private TextView inputEditText;
    private TextView outputEditText;
    private FrameLayout buttonsFrameLayout;
    private ArrayList<Button> buttons;

    public CalculatorPasscodeScreen(Context context, PasscodeEnteredDelegate delegate) {
        this.context = context;
        this.delegate = delegate;
    }

    @Override
    public View createView() {

        backgroundFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.setWillNotDraw(false);

        inputEditText = new TextView(context);
        inputEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        inputEditText.setTextColor(0xFFFFFFFF);
        inputEditText.setEllipsize(TextUtils.TruncateAt.START);
        inputEditText.setSingleLine();
        backgroundFrameLayout.addView(inputEditText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT, 70, 0, 70, 0));

        outputEditText = new TextView(context);
        outputEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
        outputEditText.setTextColor(0xFFFFFFFF);
        backgroundFrameLayout.addView(outputEditText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT, 70, 0, 70, 0));

        buttonsFrameLayout = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                if ((getParent() instanceof View)) {
                    int parentHeight = ((View) getParent()).getHeight();
                    int height = getHeight();
                    float scale = Math.min((float) parentHeight / height, 1f);

                    setPivotX(getWidth() / 2f);
                    setPivotY(((LayoutParams) getLayoutParams()).gravity == Gravity.CENTER ? getHeight() / 2f : 0);
                    setScaleX(scale);
                    setScaleY(scale);
                }
            }
        };
        backgroundFrameLayout.addView(buttonsFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        buttons = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            Button button = createButton(i);
            buttons.add(button);
        }
        for (int a = 19; a >= 0; a--) {
            Button button = buttons.get(a);
            if (a == 0) {
                buttonsFrameLayout.addView(button, LayoutHelper.createFrame(BUTTON_SIZE + BUTTON_X_MARGIN + BUTTON_SIZE, BUTTON_SIZE, Gravity.BOTTOM | Gravity.LEFT));
            } else {
                buttonsFrameLayout.addView(button, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE, Gravity.BOTTOM | Gravity.LEFT));
            }
        }
        return backgroundFrameLayout;
    }

    private Button createButton(int i) {
        Button button = new Button(context);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        ScaleStateListAnimator.apply(button, .15f, 1.5f);
        int row = i / BUTTONS_COL_COUNT;
        int col = i % BUTTONS_COL_COUNT;
        button.setTextColor(getButtonTextColor(row, col));
        int backgroundColor = getButtonBackgroundColor(row, col);
        Drawable background = Theme.createSimpleSelectorRoundRectDrawable(dp(BUTTON_SIZE / 2), backgroundColor, backgroundColor);
        button.setBackground(background);
        String buttonText = getButtonText(row, col);
        button.setTag(buttonText);
        button.setText(buttonText);
        button.setOnClickListener(this::onButtonClicked);
        return button;
    }

    private int getButtonBackgroundColor(int row, int col) {
        if (col == BUTTONS_COL_COUNT - 1) {
            return 0xfffe9500;
        } else if (row == 4) {
            return 0xffa5a5a5;
        } else {
            return 0xff323232;
        }
    }

    private int getButtonTextColor(int row, int col) {
        if (row == BUTTONS_ROW_COUNT - 1 && col != BUTTONS_COL_COUNT - 1) {
            return 0xff060606;
        } else {
            return 0xffffffff;
        }
    }

    private String getButtonText(int row, int col) {
        if (row >= 1 && row < BUTTONS_ROW_COUNT - 1 && col <= 2) {
            int num = (row - 1) * 3 + col + 1;
            return String.valueOf(num);
        } else if (col == 3) {
            if (row == 0) {
                return "=";
            } else if (row == 1) {
                return "+";
            } else if (row == 2) {
                return "-";
            } else if (row == 3) {
                return "×";
            } else if (row == 4) {
                return "/";
            }
        } else if (row == BUTTONS_ROW_COUNT - 1) {
            if (col == 0) {
                return "AC";
            } else if (col == 1) {
                return "⌫";
            } else if (col == 2) {
                return "%";
            }
        } else if (row == 0) {
            if (col == 0) {
                return "0";
            } else if (col == 1) {
                return "";
            } else if (col == 2) {
                return ".";
            }
        }
        return "";
    }

    private void onButtonClicked(View view) {
        String tag = (String) view.getTag();
        if (tag.equals("=")) {
            doEquals();
        } else if (tag.equals("⌫")) {
            deleteLastInputChar();
        } else if (tag.equals("AC")) {
            clearInput();
        } else {
            addCharToInput(tag.charAt(0));
        }
        if (inputEditText.length() == 4) {
            delegate.passcodeEntered(getPasswordString());
        }
    }

    private void doEquals() {
        try {
            String outputString = outputEditText.getText().toString()
                    .replace(getDecimalSeparator(), '.');
            BigDecimal outputValue = new BigDecimal(outputString);
            setInput(removeFractionZeroesFromString(outputValue.toPlainString(), getLocale()));
        } catch (Exception ignore) {
            clearInput();
        }
    }

    private String getPasswordString() {
        return (String) inputEditText.getText();
    }

    @Override
    public void onShow(boolean fingerprint, boolean animated) {
        Activity parentActivity = AndroidUtilities.findActivity(context);
        if (parentActivity != null) {
            View currentFocus = parentActivity.getCurrentFocus();
            if (currentFocus != null) {
                currentFocus.clearFocus();
                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
            }
        }
        backgroundFrameLayout.setBackgroundColor(0xff000000);

        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN) {
            buttonsFrameLayout.setVisibility(View.VISIBLE);
            inputEditText.setVisibility(View.VISIBLE);
            outputEditText.setVisibility(View.VISIBLE);
        }
        inputEditText.setText("");
        outputEditText.setText("");
        inputString = "";
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.displaySize.y - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);

        int sizeBetweenButtonsX = dp(BUTTON_X_MARGIN);
        int sizeBetweenButtonsY = dp(BUTTON_Y_MARGIN);
        int buttonSize = dp(BUTTON_SIZE);

        final boolean landscape = !AndroidUtilities.isTablet() && context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        int buttonsSumHeight = 2 * sizeBetweenButtonsY + (buttonSize + sizeBetweenButtonsY) * 4 + buttonSize;
        int targetButtonsSumHeight = (int)(0.65 * height);
        if (targetButtonsSumHeight >= buttonsSumHeight) {
            int spacesHeight = 2 * sizeBetweenButtonsY + 4 * sizeBetweenButtonsY;
            int spacesTargetHeight = targetButtonsSumHeight - 5 * buttonSize;
            float scale = (float)spacesTargetHeight / spacesHeight;
            sizeBetweenButtonsY = (int)(scale * sizeBetweenButtonsY);
        }
        if (landscape) {
            sizeBetweenButtonsY = 0;
        }
        for (int a = 0; a < 20; a++) {
            FrameLayout.LayoutParams layoutParams;
            int row = a / BUTTONS_COL_COUNT;
            int col = a % BUTTONS_COL_COUNT;
            Button button = buttons.get(a);
            layoutParams = (FrameLayout.LayoutParams) button.getLayoutParams();
            layoutParams.bottomMargin = 2 * sizeBetweenButtonsY + (buttonSize + sizeBetweenButtonsY) * row;
            layoutParams.leftMargin = (buttonSize + sizeBetweenButtonsX) * col;
            button.setLayoutParams(layoutParams);
        }

        if (landscape) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) buttonsFrameLayout.getLayoutParams();
            layoutParams.leftMargin = width / 2;
            layoutParams.topMargin = Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0;
            layoutParams.width = width / 2;
            layoutParams.height = height;
            layoutParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
            buttonsFrameLayout.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) inputEditText.getLayoutParams();
            layoutParams.bottomMargin = buttonSize + 2 * sizeBetweenButtonsY + 2 * AndroidUtilities.dp(52);
            layoutParams.leftMargin = sizeBetweenButtonsX;
            layoutParams.rightMargin = width / 2 + sizeBetweenButtonsX;
            layoutParams.width = LayoutHelper.WRAP_CONTENT;
            inputEditText.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) outputEditText.getLayoutParams();
            layoutParams.bottomMargin = buttonSize + 2 * sizeBetweenButtonsY;
            layoutParams.leftMargin = sizeBetweenButtonsX;
            layoutParams.rightMargin = width / 2 + sizeBetweenButtonsX;
            layoutParams.width = LayoutHelper.WRAP_CONTENT;
            outputEditText.setLayoutParams(layoutParams);
        } else {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) buttonsFrameLayout.getLayoutParams();
            layoutParams.leftMargin = 0;
            layoutParams.topMargin = 0;
            layoutParams.width =  buttonSize * BUTTONS_COL_COUNT + sizeBetweenButtonsX * Math.max(0, BUTTONS_COL_COUNT - 1);
            layoutParams.height = dp(82) + targetButtonsSumHeight;
            if (AndroidUtilities.isTablet()) {
                layoutParams.gravity = Gravity.CENTER;
            } else {
                layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            }
            buttonsFrameLayout.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) inputEditText.getLayoutParams();
            layoutParams.bottomMargin = buttonsSumHeight + buttonSize + 2 * sizeBetweenButtonsY + AndroidUtilities.dp(52) + 2 * sizeBetweenButtonsY;
            layoutParams.leftMargin = sizeBetweenButtonsX;
            layoutParams.rightMargin = sizeBetweenButtonsX;
            layoutParams.width = LayoutHelper.WRAP_CONTENT;
            inputEditText.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) outputEditText.getLayoutParams();
            layoutParams.bottomMargin = buttonsSumHeight + buttonSize + 2 * sizeBetweenButtonsY;
            layoutParams.leftMargin = sizeBetweenButtonsX;
            layoutParams.rightMargin = sizeBetweenButtonsX;
            layoutParams.width = LayoutHelper.WRAP_CONTENT;
            outputEditText.setLayoutParams(layoutParams);
        }
    }

    private void deleteLastInputChar() {
        if (inputString.isEmpty()) {
            return;
        }
        inputString = inputString.substring(0, inputString.length() - 1);
        inputEditText.setText(inputString);
        updateOutput();
    }

    private void clearInput() {
        inputString = "";
        inputEditText.setText(inputString);
        updateOutput();
    }

    private void setInput(String input) {
        inputString = input;
        inputEditText.setText(inputString);
        updateOutput();
    }

    private void addCharToInput(char c) {
        if (inputString.isEmpty()) {
            if ("+×/%.".contains(String.valueOf(c))) {
                return;
            }
        } else {
            char lastChar = inputString.charAt(inputString.length() - 1);
            if (c == '.') {
                if (!"0123456789".contains(String.valueOf(lastChar))) {
                    return;
                }
                for (int i = inputString.length() - 1; i >= 0; i--) {
                    char currentChar = inputString.charAt(i);
                    if (currentChar == getDecimalSeparator()) {
                        return;
                    } else if (!"0123456789".contains(String.valueOf(currentChar))) {
                        break;
                    }
                }
                c = getDecimalSeparator();
            } else if ("+-×/".contains(String.valueOf(c))) {
                if ("+-×/".contains(String.valueOf(lastChar))) {
                    inputString = inputString.substring(0, inputString.length() - 1);
                }
            } else if (c == '%') {
                if (!"0123456789%".contains(String.valueOf(lastChar))) {
                    return;
                }
            }
        }
        inputString += c;
        inputEditText.setText(inputString);
        updateOutput();
    }

    private void updateOutput() {
        try {
            BigDecimal result = CalculatorHelper.calculateExpression(inputString, getDecimalSeparator());
            outputEditText.setText(formatBigDecimal(result, getLocale()));
        } catch (Exception ignore) {
            outputEditText.setText("Error");
        }
    }

    private static String formatBigDecimal(BigDecimal value, Locale locale) {
        if (value == null) {
            return "";
        } else if (value.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        } else {
            String plainOutput = value.toPlainString();
            if (plainOutput.length() <= 10) {
                return removeFractionZeroesFromString(plainOutput, locale)
                        .replace('.', getDecimalSeparator(locale));
            } else {
                String output = String.format(locale, "%." + Math.min(value.precision(), 7) + "g", value);
                return removeFractionZeroesFromString(output, locale);
            }
        }
    }

    private static String removeFractionZeroesFromString(String output, Locale locale) {
        char decimalSeparator = getDecimalSeparator(locale);
        if (output.contains(String.valueOf(decimalSeparator))) {
            while (output.endsWith("0")) {
                output = output.substring(0, output.length() - 1);
            }
        }
        if (output.endsWith(String.valueOf(decimalSeparator))) {
            output = output.substring(0, output.length() - 1);
        }
        return output;
    }

    private char getDecimalSeparator() {
        return getDecimalSeparator(getLocale());
    }

    private static char getDecimalSeparator(Locale locale) {
        return DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
    }

    private Locale getLocale() {
        return context.getResources().getConfiguration().locale;
    }
}
